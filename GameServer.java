package serverCore;

import java.net.*;
import java.util.*;

import binary_serialization.*;
import game_core.*;
import packets.*;
import utils.*;

public class GameServer extends Server {
	
	private final int MAX_ATTEMPTS = 10;
	
	private List<Player> players = Collections.synchronizedList(new LinkedList<Player>());
	private LinkedList<Integer> playerTimeoutResponse = new LinkedList<Integer>();
	
	private Thread gameUpdateThread;
	private Thread inputThread;
	
	private double tickRate = 1.0 / 60.0;
	private double sendRate = 1.0 / 20.0;
	private int currentFrame = 0;
	
	private long lastMovementStateSent = System.currentTimeMillis();
	
	List<Integer> list = Collections.synchronizedList(new ArrayList<Integer>());
	
	private LinkedList<Integer> availableIDs = new LinkedList<Integer>();
	
	private MiniGame currentMiniGame = new MiniGame();

	private long startTime = System.currentTimeMillis();
	
	public GameServer(int port) {
		super(port);
		gameUpdateThread = new Thread(() -> update(), "GameServer update Thread");
		inputThread = new Thread(() -> handleAdminInput(), "Game Server Admin Input Handler Thread");
		for(int i = 1; i < 32; i++)
			availableIDs.add(i);
	}
	
	private void handleAdminInput() {
		Scanner scanner = new Scanner(System.in);
		while(running) {
			String nextLine = scanner.nextLine();
			switch (nextLine) {
			case "/uptime":
				System.out.println("Input was uptime");
				break;
			default:
				break;	
			}
			if(nextLine.equalsIgnoreCase("/uptime")) {
				System.out.println("Server uptime: " + (System.currentTimeMillis() - startTime) / 1000 + " s");
			} else if (nextLine.equalsIgnoreCase("/stop")) {
				System.out.println("Server shut down initiated...");
				close();
			}
		}
		scanner.close();
	}
	
	@Override
	public void start() {
		super.start();
		gameUpdateThread.start();
		inputThread.start();
		
		//TODO: REMOVE THIS TEST WHEN WORKING PROPERLY
		for(int i = 0; i < 16; i++) {
			try {
				Player newPlayer = new Player(i, "Seppu " + i, "hahmo " + i, InetAddress.getByName("80.222.238.22"), 25565);
				newPlayer.position = new Point2f(123.343f, 2131.3333f);
				newPlayer.rotation = -2.3f;
				//players.add(newPlayer);
			} catch (Exception e) {
				System.err.println("error initializing address");
			}
		}
	}

	private void update() {
		double timePassed = 0;
		double deltaTime = 0;
		double lastUpdateTime = System.currentTimeMillis();
		
		int fixedUps = 0;
		int totalUps = 0;
		
		double lastTickRateCheck = System.currentTimeMillis();
		
		while(running) {
			double currentTime = System.currentTimeMillis();
			
			totalUps++;
			
			while (timePassed >= tickRate) {
				fixedUps++;
				long fixedUpdateTime = System.currentTimeMillis();
				updateFixedStep();
				fixedUpdateTime = System.currentTimeMillis() - fixedUpdateTime;
				timePassed -= tickRate;
				try {
					long timeToSleep = (long) (tickRate * 1000 - fixedUpdateTime);
					if (timeToSleep >= 5) {
						Thread.sleep(timeToSleep - 5);
					}
					
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
			updateDynamicStep(deltaTime);
			deltaTime = (currentTime - lastUpdateTime) / 1000.0;
			lastUpdateTime = currentTime;
			timePassed += deltaTime;
			
			if(currentTime - lastTickRateCheck >= 1000) {
				if(ServerFrame.textFieldTexts.isEmpty())
					ServerFrame.textFieldTexts.add("\n Fixed updates: " + fixedUps + ", dynamic updates: " + totalUps);
				else {
					ServerFrame.textFieldTexts.poll();
					ServerFrame.textFieldTexts.add("\n Fixed updates: " + fixedUps + ", dynamic updates: " + totalUps);
				}
				lastTickRateCheck = currentTime;
				fixedUps = 0;
				totalUps = 0;
			}
		}
	}
	
	// TODO: Process packets accordingly
	@Override
	protected void dataReceived(final byte[] data, final InetAddress address, final int port) {
		//echoBack(packet);
		
		BitReadStream dataStream = DataHandler.handle(data);
		if(dataStream != null) {
			switch(dataStream.type) {
			case PacketType.CONNECTION:
				// Check if the player is already connected
				// If not, add him REMEMBER TO ASSIGN ITS ADDRESS AND PORT VALUES TOO!!
				System.out.println("Got a connection packet!");
				ConnectionPacket connection = new ConnectionPacket(dataStream);
				boolean playerFound = false;
				for(Player p : players)
					if(p.unifiedIdentifier.contentEquals(connection.unifiedIdentifier)) {
						playerFound = true;
						p.address = address;
						p.port = port;
						System.out.println("Player found with the same unified identifier");
						ConnectionAcceptPacket accept = new ConnectionAcceptPacket(p.playerID, p.name, p.charater, p.unifiedIdentifier);
						send(accept, p);
					}
				if(!playerFound) {
					Player newPlayer = new Player(availableIDs.removeFirst(), connection.name, connection.character, address, port);
					newPlayer.unifiedIdentifier = connection.unifiedIdentifier;
					players.add(newPlayer);
					ConnectionAcceptPacket accept = new ConnectionAcceptPacket(newPlayer.playerID, newPlayer.name, newPlayer.charater, newPlayer.unifiedIdentifier);
					send(accept, newPlayer);
				}
					
				break;
			case PacketType.CONNECTION_ACCEPT:
				System.out.println("Received connection accept packet?");
				break;
			case PacketType.DISCONNECT:
				System.out.println("Received disconnection packet");
				DisconnectPacket disconnection = new DisconnectPacket(dataStream);
				for(Player p : players)
					if(p.playerID == disconnection.playerID) {
						disconnect(p, true);
						break;
					}
				break;
			case PacketType.TIME_OUT:
				System.out.println("Got a time out response!");
				TimeOutCheckPacket timeOutResponse = new TimeOutCheckPacket(dataStream);
				playerTimeoutResponse.add(timeOutResponse.playerID);
				break;
			case PacketType.RECONNECT_REQUEST:
				System.out.println("Received reconnect request packet?");
				break;
			case PacketType.MOVEMENT_F_CLIENT:
				MovementPacket movementPacket = new MovementPacket(dataStream);
				if(movementPacket != null) {
					boolean playerConnected = false;
					for(Player p : players) {
						if(p.playerID == movementPacket.playerID) {
							p.updateWith(movementPacket);
							playerConnected = true;
						}
					}
					if (!playerConnected) {
						Player playerToConnect = new Player((int) movementPacket.playerID, "noname", "undefined", address, port);
						send(new ReconnectRequestPacket(), playerToConnect);
						System.out.println("Asking the player to reconnect!");
						System.out.println(address.toString() + ":" + port);
					}
				}
				break;
			case PacketType.SERVER_UPDATE_PACKET_TO_CLIENT:
				ServerUpdatePacket ser = new ServerUpdatePacket(dataStream);
				for(MovementPacket m : ser.movementPackets)
					System.out.println("x: " + m.position.x + ", y: " + m.position.y + ", rot: " + m.theta + ", playerID: " + m.playerID);
				break;
			case PacketType.PLAYER_LIST_REQUEST:
				ConnectionAcceptPacket[] connectionAcceptPackets = new ConnectionAcceptPacket[players.size()];
				for(int i = 0; i < players.size(); i++) {
					connectionAcceptPackets[i] = new ConnectionAcceptPacket(players.get(i).playerID, players.get(i).name, players.get(i).charater, players.get(i).unifiedIdentifier);
				}
				PlayerListRequest listResponse = new PlayerListRequest(connectionAcceptPackets);
				sendToAll(listResponse);
				break;
			default:
				System.out.println("Got a packet with an odd type???? Or the data was obfuscated");	
				break;
			}
		}
	}

	//TODO: Implement connection handling
	@Override
	protected void handleConnections() {
		// Send time out check packet 
		for(int i = 0; i < players.size(); i++) {
			Player player = players.get(i);
			if(!playerTimeoutResponse.contains(player.playerID)) {
				if(player.attempts >= MAX_ATTEMPTS) {
					disconnect(player, false);
				} else {
					TimeOutCheckPacket timeOutCheck = new TimeOutCheckPacket(player.playerID);
					send(timeOutCheck, player);
					player.attempts++;
				}
			} else {
				playerTimeoutResponse.remove(new Integer(player.playerID));
				player.attempts = 0;
			}
		}
		
	}
	
	private void updateDynamicStep(double deltaTime) { }
	
	private void updateFixedStep() {
		currentFrame++;
		if(currentFrame > 100000)
			currentFrame = 0;
		
		if(currentFrame % 300 == 0) {
			ConnectionAcceptPacket[] packets = new ConnectionAcceptPacket[players.size()];
			for(int i = 0; i < packets.length; i++) {
				Player p = players.get(i);
				ConnectionAcceptPacket packet = new ConnectionAcceptPacket(p.playerID, p.name, p.charater, p.unifiedIdentifier);
				packets[i] = packet;
			}
			PlayerListRequest playerList = new PlayerListRequest(packets);
			sendToAll(playerList);
		}
		
		switch (currentMiniGame.type) {
		case MiniGame.LOBBY:
			lobbyUpdate();
			break;
		case MiniGame.PLATFORMER:
			platformerUpdate();
			break;
		case MiniGame.CLICKER:
			clickerUpdate();
			break;
		case MiniGame.MARIO_CART:
			marioCartUpdate();
			break;
		case MiniGame.CANNON:
			cannonUpdate();
			break;
		case MiniGame.BUMPER:
			bumperUpdate();
			break;
		case MiniGame.BOSS_FIGHT:
			bossFightUpdate();
			break;
		}
		
		
	}
	
	private void lobbyUpdate() {
		updateMovementStates();
	}
	
	private void platformerUpdate() {
		updateMovementStates();
	}
	
	private void clickerUpdate() {
		
	}
	
	private void marioCartUpdate() {
		updateMovementStates();
	}
	
	private void cannonUpdate() {
		
	}
	
	private void bumperUpdate() {
		updateMovementStates();
	}
	
	//TODO: Perustuen oikeaan minigameen, nyt place holderina
	private void bossFightUpdate() {
		updateMovementStates(); // ??
	}
	
	private void updateMovementStates() {
		long timeSinceLastMovementUpdate = System.currentTimeMillis() - lastMovementStateSent;
		if (timeSinceLastMovementUpdate >= sendRate * 1000) {
			lastMovementStateSent = System.currentTimeMillis();
			MovementPacket[] movements = new MovementPacket[players.size()];
			for(int i = 0; i < players.size(); i++)
				movements[i] = players.get(i).getMovementPacket();
			
			ServerUpdatePacket serverUpdate = new ServerUpdatePacket(movements);
			sendToAll(serverUpdate);

		}
	}
	
	@Override
	public void close() {
		try {
			gameUpdateThread.join(1000);
			inputThread.join(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		super.close();
	}

	public synchronized Vector<String> getPlayerNames() {
		Vector<String> result = new Vector<String>();
		for(Player p : players)
			result.add(p.name + ", character: " + p.charater + ", (" + p.position.x + ", " + p.position.y + "), rot: " + p.rotation + ", ID: " + p.playerID);
		return result;
	}
	
	protected synchronized void sendToAll(PacketProtocol packet) {
		for(Player p : players)
			send(packet, p);
	}
	
	protected synchronized void send(PacketProtocol packet, Player toPlayer) {
		BitWriteStream writeStream = new BitWriteStream(packet.type);
		packet.encode(writeStream);
		byte[] dataToSend = writeStream.packData();
		send(dataToSend, toPlayer);
	}
	
	protected synchronized void send(byte[] data, Player toPlayer) {
		send(data, toPlayer.address, toPlayer.port);
	}
	
	private void disconnect(Player player, boolean playerDecision) {
		availableIDs.add(player.playerID);

		for(int i = 0; i < players.size(); i++) {
			if (players.get(i).playerID == player.playerID) {
				players.remove(i);
				break;
			}
		}
	}
	
	private void print(short[] data) {
		System.out.println("----------------------");
		System.out.print("|");
		for(short b : data) {
			System.out.print(b + "|");
		}
		System.out.println(" ");
		System.out.println("----------------------");
	}
	
	private void print(byte[] data) {
		System.out.println("----------------------");
		System.out.print("|");
		for(byte b : data) {
			System.out.print(unSign(b) + "|");
		}
		System.out.println(" ");
		System.out.println("----------------------");
	}
	
	private short unSign(byte b) {
		return (short) (b >= 0 ? (short) b : ((short) (b) + 256));
	}
	
}
