import java.net.*;

/**
 * This is the listening thread for the HalServer class. While this
 * is a thread, there should be always be only either 0 or 1 instance
 * of it running. It starts the server and listens for connections and
 * is its own seperate thread so that it will not interfere with the
 * terminal and processing taking place in the HalServer thread.
 * <p><p>
 * 
 * All this thread does in a nutshell is create a serverSocket and listen
 * on the specified port. If a connection is established then it gives a socket,
 * starts it a HalClientThread and then goes back to listening again.
 * 
 * @author  Evan Lennick <eml0300@mail.widener.edu>
 * @version 1.0
 */
public class HalListener extends Thread
{
	private boolean listening = false;
	private HalServer server = null;
	private Socket socket = null;
	private ServerSocket serverSocket = null;
	private int port = 0;

	/**
	 * The thread constructor.
	 * 
	 * @param  server  The HalServer that started this thread.
	 * @param  port    The port to listen for connections on.
	 */
	HalListener(HalServer server, int port)
	{
		this.server = server;
		this.port = port;
	}

	/**
	 * The main process of this thread. It listens for connections and starts
	 * a client thread for them when they connect.
	 */
	public void run()
	{
		try
		{
			//start server
			try
			{
				serverSocket = new ServerSocket(port);
				serverSocket.setSoTimeout(250);
			}
			catch(BindException be)
			{
				server.sendMessage("Unable to start server because port " + port + " is already in use.");
				return;
			}

			listening = true;
			printStatus();
			//listen for connections
			while(listening)
			{
				try
				{
					socket = serverSocket.accept();
				}
				catch(SocketTimeoutException ste){}
				catch(SocketException se){ return; }

				if(socket != null)
				{
					HalClientThread client = new HalClientThread(socket, server);
					client.start();
					server.sendMessage("New client connection from " + client.getIp());
					socket = null;
				}
			}
		}
		catch(Exception e)
		{
			System.err.println("*** Exception: " + e.getMessage());
		}
	}

	/**
	 * A method that returns the port currently being listened to.
	 * 
	 * @return  int  Port being listened to for connections.
	 */
	public int getListeningPort()
	{
		if(serverSocket == null)
			return 0;

		return serverSocket.getLocalPort();
	}

	/**
	 * Prints out a list of server information.
	 */
	public void printStatus()
	{
		try
		{
			server.sendMessage("Server address:       \t" + InetAddress.getLocalHost());
			server.sendMessage("Server listening port:\t" + getListeningPort());
			server.sendMessage(server.listUsers());
		}
		catch(Exception e)
		{
			System.err.println("*** Error getting localhost address.");
			System.err.println("*** Exception: " + e.getMessage());
		}
	}

	/**
	 * Stops the server. Stops listening for connections and closes the ServerSocket.
	 */
	public void halt()
	{
		listening = false;

		try
		{
			if(serverSocket != null)
			{
				serverSocket.close();
				serverSocket = null;
			}

			server.sendMessage("Server stopped.");
		}
		catch(Exception e){}
	}
}