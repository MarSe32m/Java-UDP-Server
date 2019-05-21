import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Scanner;

public class ExampleServer extends Server {

	private Thread commandHandler;
	private long startTime = System.currentTimeMillis();
	
	public ExampleServer(int port) {
		super(port);
		commandHandler = new Thread(() -> handleCommands(), "Server Command Thread");
		setUpdatePeriod(1000);
	}

	@Override
	public void start() {
		super.start();
		commandHandler.start();
		System.out.println("Server started...");
	}
	
	@Override
	protected void periodicUpdate() {
		System.out.println("The server is in good health!");
		// Use this method to handle connections (meaning your own implementation of connections)
		// or you can use this to make periodical updates to the game state, application state etc.
	}

	@Override
	protected void dataReceived(byte[] data, InetAddress address, int port) {
		System.out.println("Data received! The senders address is " + address.toString() + ":" + port);
		//TODO: Now let's do something with the data
	}
	
	@Override
	public void close() {
		super.close();
		try {
			commandHandler.join(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("Server shutdown successfully!");
	}
	
	private void handleCommands() {
		Scanner scanner = new Scanner(System.in);
		
		while (true) {
			String command = scanner.nextLine();
			if(command.equalsIgnoreCase("/info")) {
				stopPeriodicUpdates();
				System.out.println("Requested server info...");
				System.out.println("Server uptime: " + (System.currentTimeMillis() - startTime) + " ms");
			} else if (command.equalsIgnoreCase("/send")) {
				resumePeriodicUpdates();
				System.out.println("Sending hello world!");
				try {
					send("Hello world!".getBytes(), InetAddress.getByName("localhost"), 25565);
				} catch (UnknownHostException e) {
					e.printStackTrace();
				}
			} else if(command.equalsIgnoreCase("/exit")) {
				close();
				break;
			}
		}
		
		scanner.close();
	}
	
	

}
