
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
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
	
	private volatile boolean running = false;
	private volatile boolean periodicUpdatesRunning = true;
	
	/**
	 * A thread used for receiving client packets
	 */
	private Thread receiveThread;
	
	/**
	 * A thread to dispatch the packets to subclass implementations
	 */
	private Thread packetProcessingThread;
	
	/**
	 * A thread to make periodic updates
	 */
	private Thread periodicUpdateThread;
	
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

	/**
	 * Used for calculating received and sent bytes
	 */
	
	private ConcurrentLinkedQueue<Integer> byteLDown = new ConcurrentLinkedQueue<>();
	private ConcurrentLinkedQueue<Integer> byteLUp = new ConcurrentLinkedQueue<>();
	
	private int updatePeriod = 2500;
	
	public Server(int port) {
		this.port = port;
	}
	
	public void start() {
		try {
			serverSocket = new DatagramSocket(port);
		} catch (SocketException e) {
			e.printStackTrace();
			System.err.println("Couldn't start server, error ocurred!");
			System.err.println("Are you sure you haven't already executed a server on this port?");
			return;
		}
		running = true;
		
		receiveThread = new Thread(() -> listen(), "Receive Thread");
		packetProcessingThread = new Thread(() -> processPackets(), "Packet Processing Thread");
		periodicUpdateThread = new Thread(() -> dispatchPeriodicUpdates(), "Periodic Updates Thread");
		
		receiveThread.start();
		packetProcessingThread.start();
		periodicUpdateThread.start();
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
	}
		
	/**
	 * Calls periodicUpdate every updatePeriod
	 */
	private void dispatchPeriodicUpdates() {
		while(periodicUpdatesRunning && running) {
			try {
				Thread.sleep(updatePeriod);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			periodicUpdate();
		}
		try {
			periodicUpdateThread.join(1000);
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
						Thread.sleep(10);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
		try {
			packetProcessingThread.join(1000);
		} catch (InterruptedException e) {
			System.out.println("Server closing failed!");
			e.printStackTrace();
		}
	}
	
	/**
	 * This method is called every 2.5 seconds
	 */
	
	protected abstract void periodicUpdate();
	
	/**
	 * 
	 * @param data is the received data
	 * @param address is the address of the sender
	 * @param port is the port of the sender
	 */
	protected abstract void dataReceived(final byte[] data, final InetAddress address, final int port);
	
	protected final synchronized void send(final byte[] data, final InetAddress address, final int port) {
		DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
		send(packet);
	}
	
	protected final synchronized void send(final DatagramPacket packet) {
		byteLUp.add(packet.getLength());
		try {
			serverSocket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
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
	
	
	public void setUpdatePeriod(int period) {
		this.updatePeriod = period;
	}
	
	public void stopPeriodicUpdates() {
		periodicUpdatesRunning = false;
	}

	public void resumePeriodicUpdates() {
		periodicUpdatesRunning = true;
		periodicUpdateThread = new Thread(() -> dispatchPeriodicUpdates(), "Periodic Updates Thread");
		periodicUpdateThread.start();
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
	
	public synchronized String getPacketSummary() {
		return packetCount + " packets processed";
	}
	
	public synchronized int getDownloadBandwidth() {
		return bytesPerSecondDown;
	}
	
	public synchronized int getUploadBandwidth() {
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
			periodicUpdateThread.join(1000);
		} catch (InterruptedException e) {
			System.out.println("Server closing failed!");
			e.printStackTrace();
		}
	}
	
}