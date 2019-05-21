# Java-UDP-Server
UDP Server

This is an abstraction over datagram sockets.
It's intended to be easy to use and expand on.

ExampleServer is an example implementation of the Server class.

Instructions:
- 
Use the dataReceived(...) method to handle received data.

Use the handleConnections() for periodical updates (but not for updates like a gameloop), you can change the rate of these updates.

Use send(DatagramPacket packet) and send(byte[] data, InetAddress address, int port) to send data to peers. The latter is preferred since it creates the packet for you. 

Disclaimer
-
Now this is not at all a professional or robust implementation and should be used at your own responsibilty
This is intended to show how to implement a basic UDP server
