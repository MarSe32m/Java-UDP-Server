package org.marse.core;

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

	private DatagramSocket serverSocket;
	private int port;
	
	private volatile boolean running = false;
	private volatile boolean periodicUpdatesRunning = true;
	
	private int updatePeriod = 1000;

	// Threads
	private Thread receiveThread;
	private Thread packetProcessingThread;
	private Thread periodicUpdateThread;
	
	// Receive buffer
	private final int MAX_PACKET_SIZE = 2048;
	private byte[] receiveDataBuffer = new byte[MAX_PACKET_SIZE * 10];
	
	/**
	 * Thread safe queue for processing packets
	 */
	private ConcurrentLinkedQueue<DatagramPacket> packetsToProcess = new ConcurrentLinkedQueue<DatagramPacket>();
	
	public Server(int port) {
		this.port = port;
	}
	
	public void start() throws SocketException {
		serverSocket = new DatagramSocket(port);
		running = true;
		
		receiveThread = new Thread(() -> listen());
		packetProcessingThread = new Thread(() -> processPackets());
		periodicUpdateThread = new Thread(() -> dispatchPeriodicUpdates());
		
		receiveThread.setDaemon(true);
		
		receiveThread.start();
		packetProcessingThread.start();
		periodicUpdateThread.start();
	}
	
	
	/**
	 * Listen for incoming packets and add the to the processing queue
	 */
	private void listen() {
		while (running) {
			DatagramPacket packet = new DatagramPacket(receiveDataBuffer, MAX_PACKET_SIZE);
			try {
				serverSocket.receive(packet);
				addPacketToProcessingQueue(packet);
			} catch (SocketException se) {
				if (running)
					se.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
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
			update(updatePeriod);
		}
		try {
			periodicUpdateThread.join(1000);
		} catch (InterruptedException e) {
			Logger.error("Failed to join periodic update thread");
			e.printStackTrace();
		}
	}
	
	
	private void processPackets() {
		synchronized (packetsToProcess) {
			while(running) {
				if (!packetsToProcess.isEmpty()) {
					DatagramPacket packet = packetsToProcess.poll();
					byte[] packetData = Arrays.copyOf(packet.getData(), packet.getLength());
					dataReceived(packetData, packet.getAddress(), packet.getPort());
				} else {
					try {
						Thread.sleep(5);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
		try {
			packetProcessingThread.join(1000);
		} catch (InterruptedException e) {
			Logger.error("Failed to join packet processing queue");
			e.printStackTrace();
		}
	}
	
	public void setUpdatePeriod(int period) {
		this.updatePeriod = period;
	}
	
	public void resumePeriodicUpdates() {
		periodicUpdatesRunning = true;
		periodicUpdateThread = new Thread(() -> dispatchPeriodicUpdates(), "Periodic Update Thread");
		periodicUpdateThread.start();
	}
	
	public void stopPeriodicUpdates() {
		periodicUpdatesRunning = false;
	}

	/**
	 * This method is called every 2.5 seconds
	 */
	protected abstract void update(long deltaMillis);
	
	/**
	 * 
	 * @param data The received data
	 * @param address The address of the sender
	 * @param port The port of the sender
	 */
	protected abstract void dataReceived(final byte[] data, final InetAddress address, final int port);
	
	protected final synchronized void send(final byte[] data, final InetAddress address, final int port) {
		DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
		send(packet);
	}
	
	protected final synchronized void send(final DatagramPacket packet) {
		try {
			serverSocket.send(packet);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	
	private synchronized void addPacketToProcessingQueue(final DatagramPacket packet) {
		packetsToProcess.add(packet);
	}

	public synchronized void close() {
		Logger.info("Closing server...");
		running = false;
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