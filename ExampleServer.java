import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;

import org.marse.core.Logger;
import org.marse.core.Server;
import org.marse.core.Time;

public class ExampleServer extends Server {

	/**
	 * Separate thread for handling commands from the server "admin"
	 */
	private Thread commandHandler;
	
	private long maxUsed = 0;
	
	// Game game;
	
	public ExampleServer(int port) {
		super(port);
		commandHandler = new Thread(() -> handleCommands(), "Server Command Thread");
		//stopPeriodicUpdates();
		setUpdatePeriod(10000);
		// Start your own server loop, game loop etc.
	}
	
	@Override
	public void start() throws SocketException {
		super.start();
		commandHandler.start();
		Logger.info("Server started...");
	}
	
	@Override
	protected void update(long deltaMillis) {
		//Logger.info(memUsageString());
		// Use this method to handle connections (meaning your own implementation of connections)
		// or you can use this to make periodical updates to the game state, application state etc.
		// or you can disable it all together with stopPeriodicUpdates() method.
		
	}

	@Override
	protected void dataReceived(byte[] data, InetAddress address, int port) {
		Logger.info("Data received! The senders address is " + address.toString() + ":" + port);
	}
	
	@Override
	public void close() {
		super.close();
		try {
			commandHandler.join(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		Logger.info("Server shutdown successful");
	}
	
	private void handleCommands() {
		Scanner scanner = new Scanner(System.in);
		
		while (true) {
			String nextLine = scanner.nextLine();
			if(!nextLine.startsWith("/")) { continue; }
			String command = nextLine.split("/")[1];
			if(command.equalsIgnoreCase("info") || command.equalsIgnoreCase("uptime")) {
				Logger.info("Server uptime: " + Time.getTotalSeconds() + " s");
				Logger.info(memUsageString());
			} else if (command.equalsIgnoreCase("send")) {
				Logger.info("Sending hello world!");
				try {
					send("Hello world!".getBytes(), InetAddress.getByName("localhost"), 25565);
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
			} else if(command.equalsIgnoreCase("exit") || command.equalsIgnoreCase("stop")) {
				break;
			} else {
				System.out.println("invalid command: " + command);
			}
		}
		scanner.close();
		close();
	}
	
	
	private String memUsageString() {
		long total = Runtime.getRuntime().totalMemory();
        long free = Runtime.getRuntime().freeMemory();
        long used = total - free;
        if(used > maxUsed)
        	maxUsed = used;
        
		return "Memory used: " + (used/1024/1024) + "MB / Total: " + (total/1024/1024) + "MB. Maximum used: " + (maxUsed/1024/1024) + " MB.";
	}

}
