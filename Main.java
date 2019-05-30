import java.net.SocketException;

import org.marse.core.Logger;

public class Main {
	public static void main(String[] args) throws SocketException {
		new ExampleServer(25565).start();
	}
}
