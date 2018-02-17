import java.io.*;
import java.net.*;

/**
 * This is the client thread for the HalServer and HalListener classes. Everytime
 * a new connection is detected, a HalClientThread is created for that connection
 * and the user that logs in from that connection. All of that users information and
 * streams are stored and accessed through this thread. All clients connected to the
 * server are stored in a Vector object called "clients" that is declared in the
 * HalServer class.
 * 
 * @author  Evan Lennick <eml0300@mail.widener.edu>
 * @version 1.0
 */
class HalClientThread extends Thread
{
	//Globals
	private final int MAX_NAME_LENGTH = 12;
	private Socket socket = null;
	private HalServer server = null;
	private BufferedReader in = null;
	private PrintWriter out = null;
	private boolean connected = false;

	//Client Attributes
	private String userId = null;
	private String userIp = null;

	/**
	 * The class constructor.
	 * 
	 * @param  socket  The socket this user is bound to.
	 * @param  server  The HalServer that this client will communicate with. 
	 */
	HalClientThread(Socket socket, HalServer server)
	{
		//get client info
		this.socket = socket;
		this.server = server;
		this.userIp = socket.getInetAddress().toString();

		try
		{
			//establish client streams
			this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			this.out = new PrintWriter(socket.getOutputStream(), true);
			this.connected = true;

			//add this thread to the list of connected clients
			synchronized(server.clients)
			{
				if(server.clients.size() >= server.MAX_CLIENTS)
				{
					sendMessage("* Too many clients already connected! Please try again later...");
					disconnect();
				}
				else
					server.clients.addElement(this);
			}
		}
		catch(Exception e)
		{
			System.err.println("*** Unable to establish connection with client: " + this.userIp);
			System.err.println("*** Exception: " + e.getMessage());
			disconnect();
		}
	}

	/**
	 * Return the user id that the client logged in with.
	 * 
	 * @return  String  The name of the user.
	 */
	public String toString()
	{
		return this.userId;
	}

	/**
	 * The main process of the client thread. Logs in the user and then listens to the
	 * client for input. Any non-null, non-blank input will be processed by the server.
	 */
	public void run()
	{

		userId = aquireUserId();

		if(userId == null || userId.trim().equals("null"))
		{
			sendMessage("* Error logging in!");
			disconnect();
		}

		sendMessage("Welcome, " + userId + "!");
		sendMessage("");
		sendMessage(server.motd.toString());
		sendMessage("");
		sendMessage(server.listUsers());
		sendMessage("-----");

		server.broadcast(this.userId + " has entered the room.");

		String input = null;

		while(connected)
		{
			try
			{
				try
				{
					input = (in.readLine()).trim();
				}
				catch(SocketException se)
				{
					System.err.println("*** Error reading input from user/client: " + userId + "/" + userIp);
					System.err.println("*** Exception: " + se.getMessage());
					disconnect();
				}

				if(input != null && !input.trim().equals(""))
					server.processInput(this, input);
			}
			catch(NullPointerException npe)
			{
				return;
			}
			catch(Exception e)
			{
				System.err.println("*** Error communicating with user/client: " + userId + "/" + userIp);
				System.err.println("*** Exception: " + e.getMessage());
			}
		}
	}

	/**
	 * Assigns the user a login name. 
	 * 
	 * NOTE: Currently aquireUserName() is used as the only method to 
	 * determine a username. This method assignUserId() is currently not used.
	 * 
	 * @return  String  The username assigned to this client.
	 */
	private String assignUserId()
	{
		//This code assigns a username to clients as they login.
		String name = null;

		try
		{
			for(int i = 0; i < server.MAX_CLIENTS; i++)
			{
				name = "Guest" + String.valueOf(i + 1);
				
				boolean nameExists = false;
				for(int j = 0; j < server.clients.size(); j++)
				{
					if(name.equals(((HalClientThread)server.clients.get(j)).getUserId()))
					{
						nameExists = true;
					}
				}

				if(!nameExists)
					break;
			}
		}
		catch(Exception e)
		{
			System.err.println("*** Error assigning name to client: " + this.getIp());
			System.err.println("*** Exception: " + e.getMessage());
			disconnect();
		}

		return name;
	}

	/**
	 * Prompts the user for a login name.
	 * 
	 * @return  String  The approved login name entered by the user.
	 */
	private String aquireUserId()
	{
		//This code allows users to choose their own username at login.
		//It is currently not implemented.
		String input = null;
		boolean valid = false;
		
		try
		{	
			while(!valid)
			{
				this.sendMessage("Login:");
				input = in.readLine();
				valid = true;

				if(input.trim().indexOf(" ") != -1)
				{
					this.sendMessage("Name cannot contain spaces. Please try another.");
					valid = false;
				}
				
				if(input.length() > MAX_NAME_LENGTH || input.length() < 1)
				{
					this.sendMessage("Name must be between 1 and " + MAX_NAME_LENGTH + " characters long.");
					valid = false;
				}

				if(server.langFilter)
				{
					for(int i = 0; i < server.filteredWords.length; i++)
					{
						if(input.toLowerCase().indexOf(server.filteredWords[i].toLowerCase()) != -1)
						{
							this.sendMessage("Please choose a less vulgar name.");
							valid = false;
						}
					}
				}

				for(int i = 0; i < server.clients.size(); i++)
				{
					if(input.equals(((HalClientThread)server.clients.get(i)).getUserId()))
					{
						this.sendMessage("That name is already being used. Please try another.");
						valid = false;
					}
				}
			}
		}
		catch(Exception e)
		{
			System.err.println("*** Error getting username for client: " + this.getIp());
			System.err.println("*** Exception: " + e.getMessage());
			e.printStackTrace();
			disconnect();
		}
		
		return input;	 
	}

	/**
	 * Returns the ip of this client.
	 * 
	 * @return  String  The users ip address.
	 */
	public String getIp()
	{
		return userIp.substring(userIp.indexOf("/") + 1, userIp.length());
	}

	/**
	 * Returns the username of this client.
	 * 
	 * @return  String  The name of this user.
	 */
	public String getUserId()
	{
		return userId;
	}

	/**
	 * Sends a message to this user. This method is synchronized so that this client
	 * will only recieve one message at a time.
	 * 
	 * @param  message  The message to send to this client.
	 */
	public synchronized void sendMessage(String message)
	{
		try
		{
			out.println(message);
			out.flush();
		}
		catch(NullPointerException npe){}
	}

	/**
	 * Disconnects this client. Closes all streams that are open, notifies other clients of
	 * the disconnection and then removes this user from the clients Vector.
	 */
	public synchronized void disconnect()
	{
		//this.sendMessage("Disconnecting...");

		this.connected = false;

		try
		{
			if(socket != null)
			{
				socket.shutdownInput();
				socket.shutdownOutput();
				socket.close();
				socket = null;
			}
		}
		catch(Exception e){}

		try
		{
			if(out != null)
			{
				out.close();
				out = null;
			}
		}
		catch(Exception e){}
		
		try
		{
			if(in != null)
			{
				in.close();
				in = null;
			}
		}
		catch(Exception e){}

		server.sendMessage("Client has disconnected: " + getIp());

		if(server.clients.contains(this))
		{
			server.broadcast(this.userId + " has left the room.");

			synchronized(server.clients)
			{
				server.clients.remove(this);
			}
		}
	}
}