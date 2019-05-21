package serverCore;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This is an abstract server implementation
 * Subclass this to make your own servers
 * 
 * @author Sebastian Toivonen
 *
 * */
public abstract class Server {

	/**
	 * The server socket used for UDP communication
	 */
	private DatagramSocket serverSocket;
	
	/**
	 * 
	 */
	private int port;
	
	protected volatile boolean running = false;
	
	/**
	 * A thread used for receiving client packets
	 */
	private Thread receiveThread;
	
	/**
	 * A thread to dispatch the packets to subclass implementations
	 */
	private Thread packetProcessingThread;
	
	/**
	 * A thread to get notifications every 2.5 seconds for example handling connections/clients
	 */
	private Thread connectionHandlingThread;
	
	/**
	 * Maximum receive buffer size
	 */
	private final int MAX_PACKET_SIZE = 2048;
	
	/**
	 * Datagram sockets received data buffer
	 */
	private byte[] receiveDataBuffer = new byte[MAX_PACKET_SIZE * 10];
	
	/**
	 * Thread safe queue for processing packets
	 */
	private ConcurrentLinkedQueue<DatagramPacket> packetsToProcess = new ConcurrentLinkedQueue<DatagramPacket>();
	

	private long packetProcessTimes = 0;
	private int packetCount = 1;
	private int bytesPerSecondDown = 0;
	private int bytesPerSecondUp = 0;

	private ConcurrentLinkedQueue<Integer> byteLDown = new ConcurrentLinkedQueue<>();
	private ConcurrentLinkedQueue<Integer> byteLUp = new ConcurrentLinkedQueue<>();
	
	public Server(int port) {
		this.port = port;
	}
	
	public void start() {
		try {
			serverSocket = new DatagramSocket(port);
		} catch (SocketException e) {
			e.printStackTrace();
			System.err.println("Couldn't start server, error ocurred!");
			return;
		}
		running = true;
		
		receiveThread = new Thread(() -> listen(), "Listening Thread");
		packetProcessingThread = new Thread(() -> processPackets(), "Packet Processing Thread");
		connectionHandlingThread = new Thread(() -> handleTimingOut(), "Handling Connections Thread");
		
		receiveThread.start();
		packetProcessingThread.start();
		connectionHandlingThread.start();
	}
	
	
	/**
	 * Listen for incoming packets and add the to the processing queu
	 */
	private void listen() {
		while (running) {
			DatagramPacket packet = new DatagramPacket(receiveDataBuffer, MAX_PACKET_SIZE);
			try {
				serverSocket.receive(packet);
			} catch (IOException e) {
				if (running)
					e.printStackTrace();
			}
			addPacketToProcessingQueue(packet);
		}
		serverSocket.close();
		System.out.println("Listen thread ended");
	}
		
	/**
	 * Calls handleConnections every 2.5 seconds
	 */
	private void handleTimingOut() {
		while(running) {
			try {
				Thread.sleep(2500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			handleConnections();
		}
		System.out.println("Timeout thread ended");
		try {
			connectionHandlingThread.join(1000);
		} catch (InterruptedException e) {
			System.out.println("Server time out handling thread closing failed!");
			e.printStackTrace();
		}
	}
	
	/**
	 * Processes the packets in the packet queue
	 * aka. sends them to the subclass to be processed
	 */
	private void processPackets() {
		long lastUpdateTime = System.currentTimeMillis();
		
		synchronized (packetsToProcess) {
			while(running) {
				if (!packetsToProcess.isEmpty()) {
					long startTime = System.nanoTime();
					DatagramPacket packet = packetsToProcess.poll();
					byte[] packetData = Arrays.copyOf(packet.getData(), packet.getLength());
					dataReceived(packetData, packet.getAddress(), packet.getPort());
					
					packetCount++;
					byteLDown.add(packet.getLength());
					
					if (System.currentTimeMillis() - lastUpdateTime > 1000) {
						lastUpdateTime = System.currentTimeMillis();
						calculateBandwidth();
					}
					
					long endTime = System.nanoTime();
					packetProcessTimes += endTime - startTime;
				} else {
					try {
						Thread.sleep(15);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
		System.out.println("Packet processing thread ended");
		try {
			packetProcessingThread.join(1000);
		} catch (InterruptedException e) {
			System.out.println("Server closing failed!");
			e.printStackTrace();
		}
	}
	
	protected abstract void handleConnections();
	protected abstract void dataReceived(final byte[] data, final InetAddress address, final int port);
	
	private void calculateBandwidth() {
		int bytesDown = 0;
		int bytesUp = 0;
		
		while(!byteLDown.isEmpty()) {
			bytesDown += byteLDown.poll();
		}
		
		while(!byteLUp.isEmpty()) {
			bytesUp += byteLUp.poll();
		}
		
		bytesPerSecondDown = bytesDown;
		bytesPerSecondUp = bytesUp;
		
		byteLDown.add(0);
		byteLUp.add(0);
	}
	
	protected void echoBack(final DatagramPacket packet) {
		send(packet);
	}
	
	protected void send(final byte[] data, final InetAddress address, final int port) {
		DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
		send(packet);
	}
	
	protected void send(final DatagramPacket packet) {
		byteLUp.add(packet.getLength());
		try {
			serverSocket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private synchronized void addPacketToProcessingQueue(final DatagramPacket packet) {
		packetsToProcess.add(packet);
	}
	
	public synchronized String getAveragePacketProcessTime() {
		long processTimeInNano = packetProcessTimes / packetCount;
		String returnString = "Average packet processing time: " + processTimeInNano / 1000 + " μs";
		if(processTimeInNano >= 1000000)
			returnString = "Average packet processing time: " + processTimeInNano / 1000000 + " ms";
		return returnString;
	}
	
	public synchronized String getSummaryOfPackets() {
		return "" + packetCount + " packets processed";
	}

	
	public abstract Vector<String> getPlayerNames();
	
	public synchronized int downloadBandwith() {
		return bytesPerSecondDown;
	}
	
	public synchronized int uploadBandwith() {
		return bytesPerSecondUp;
	}
	
	public void close() {
		System.out.println("Closing threads...");
		running = false;
		receiveThread.interrupt();
		serverSocket.close();
		try {
			receiveThread.join(1000);
			packetProcessingThread.join(1000);
			connectionHandlingThread.join(1000);
		} catch (InterruptedException e) {
			System.out.println("Server closing failed!");
			e.printStackTrace();
		}
		System.out.println("Server shutdown successfully!");
	}
	
}