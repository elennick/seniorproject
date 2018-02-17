import java.io.*;
import java.net.*;
import java.util.*;

/**
 * This is the main class for the Hal chat server. It starts the HalListener
 * thread when the server is started and stops the thread when the server is
 * stopped.
 * 
 * @author  Evan Lennick <eml0300@mail.widener.edu>
 * @version 1.0
 */
public class HalServer extends Thread
{
	//Globals
	private HalListener listener = null;
	private BufferedReader in = null;
	protected Vector clients = new Vector(0);
	protected StringBuffer motd = new StringBuffer();
	protected boolean langFilter = false;
	protected String[] filteredWords = null;

	//Constants
	protected final String MOTD_FILE = "motd.txt";
	protected final String FILTER_FILE = "filter.txt";
	protected final String VERSION = "0.1";
	protected final int DEFAULT_PORT = 4545;
	protected final int MAX_CLIENTS = 8;

	/**
	 * Starts the main thread.
	 * 
	 * @param  args  Input parameters are not used in this class.
	 */
	public static void main(String[] args)
	{
		HalServer server = new HalServer();
		server.start();
	}

	/**
	 * Initializes and starts the server terminal. The server automatically
	 * starts listening on the default port when the server program is started
	 * as well.
	 */
	public void start()
	{
		sendMessage("Initializing...");

		String input = "";

		//retrieve the login message from the location MOTD_FILE
		sendMessage("Retrieving MOTD data from file: " + MOTD_FILE);
		if(new File(MOTD_FILE).exists())
            readMotdFile(MOTD_FILE);
		else
			motd.append("Hal Server Beta " + VERSION);

		//retrieve the list of filtered words from the location FILTER_FILE
		sendMessage("Retrieving filtered words from file: " + FILTER_FILE);
		if(new File(FILTER_FILE).exists())
			readFilterFile(FILTER_FILE);
		else
			langFilter = false;

		try
		{
			//start the server 
			startServer(DEFAULT_PORT);

			//open a stream to listen to the local terminal input
			in = new BufferedReader(new InputStreamReader(System.in));

			//while the input is not "exit", listen for input and respond appropriately
			while(!input.equalsIgnoreCase("exit"))
			{
				System.out.print("> ");
				input = (in.readLine()).trim();

				//start the server
				if(input.toLowerCase().startsWith("start"))
				{
					int port = DEFAULT_PORT;

					try
					{
						port = Integer.parseInt(input.substring(input.indexOf(" "), input.length()).trim());
					}
					catch(Exception e){}

					startServer(port);
				}
				//stop the server
				else if(input.equalsIgnoreCase("stop"))
				{
					if(listener != null)
					{
						listener.halt();
						listener = null;
					}
					else
						sendMessage("Server is not running!");

					disconnectClients();
				}
				//display the server status
				else if(input.equalsIgnoreCase("status"))
				{
					if(listener == null)
						sendMessage("Server is currently not running.");
					else
						listener.printStatus();
				}
				//list the users currently connected to the server
				else if(input.equalsIgnoreCase("users"))
				{
					sendMessage(listUsers());
				}
				//broadcast a message to all users connected
				else if(input.toLowerCase().startsWith("message"))
				{
					String message = null;

					try
					{
						message = input.substring(input.indexOf(" "), input.length()).trim();
						broadcast("* Server: " + message + " *");
						sendMessage("Message sent to all clients.");
					}
					catch(Exception e)
					{
						sendMessage("Correct usage: message <message to be sent>");
					}
				}
				//kick a user, specified by name
				else if(input.toLowerCase().startsWith("kick"))
				{
					String user = null;

					try
					{
						user = input.substring(input.indexOf(" "), input.length()).trim();

						kick(user);
					}
					catch(Exception e)
					{
						sendMessage("Correct usage: kick <username to be kicked>");
					}
				}
				//break out of the loop
				else if(input.equalsIgnoreCase("exit"))
				{
					break;
				}
				//unrecognized command
				else
				{
					sendMessage("Commands: start, stop, status, users, message, kick, exit");
				}
			}
		}
		catch(Exception e)
		{
			System.err.println("*** Exception: " + e.getMessage());
			e.printStackTrace();
		}
		finally
		{
			shutdown();
			sendMessage("Exiting...");
		}
	}

	/**
	 * Start the server by starting the HalListener thread.
	 * 
	 * @param  port  Port number to start the server listening on.
	 */
	public void startServer(int port)
	{
		try
		{
			sendMessage("Starting server...");

			if(listener == null)
			{
				listener = new HalListener(this, port);
				listener.start();
			}
			else
				sendMessage("Server is already running!");
		}
		catch(Exception e)
		{
			System.err.println("*** Error starting server on port: " + port);
			System.err.println("*** Exception: " + e.getMessage());
		}
	}

	/**
	 * Process client commands. This method is synchronized to make sure that only one
	 * clients input is being processed at a time. Valid client command are "/users,
	 * /whisper, /help, /exit". Anything not matching these commands will be broadcast
	 * as a normal message.
	 * 
	 * @param   client   The client having its input processed.
	 * @param   input    The input being processed.
	 */
	public synchronized void processInput(HalClientThread client, String input)
	{
		//send a list of currently connected users to the client who requested it
		if(input.equalsIgnoreCase("/users"))
			client.sendMessage(listUsers());
		//attempt to send a whispered message to the destination client requested
		else if(input.toLowerCase().startsWith("/whisper"))
		{
			String user = null;
			String message = null;

			try
			{
				user = input.substring(input.indexOf(" "), input.indexOf(" ", input.indexOf(" ") + 1)).trim();
				message = input.substring(input.indexOf(" ", input.indexOf(" ") + 1), input.length()).trim();

				for(int i = 0; i < clients.size(); i++)
				{
					if(((HalClientThread)clients.get(i)).getUserId().toLowerCase().equals(user.toLowerCase()))
					{
						((HalClientThread)clients.get(i)).sendMessage(client.getUserId() + " (whispered): " + message);
						client.sendMessage("To " + ((HalClientThread)clients.get(i)).getUserId() + " (whispered): " + message);
						return;
					}
				}

				client.sendMessage("* Cannot find user: " + user);
			}
			catch(Exception e)
			{
				client.sendMessage("* Correct usage: /whisper <username> <message>");
			}
		}
		//send the client a list of commands
		else if(input.equalsIgnoreCase("/help"))
			client.sendMessage("* Commands: /users /whisper /exit /help");
		//disconnect the client
		else if(input.equalsIgnoreCase("/exit"))
			client.disconnect();
		//broadcast the client input to every other client connected
		else
			broadcast(client, input);
	}

	/**
	 * Broadcast a message to all clients and label it as being from the client who sent it.
	 * If the language filter is on then filter the message before it is sent out. This method
	 * is synchronized to make sure that messages broadcast one by one.
	 * 
	 * @param   client   The client who sent this message.
	 * @param   message  The message to be broadcast.
	 */
	public synchronized void broadcast(HalClientThread client, String message)
	{
		if(langFilter)
			message = filter(message);
		
		for(int i = 0; i < clients.size(); i++)
			((HalClientThread)clients.get(i)).sendMessage(client.getUserId() + ": " + message);
	}

	/**
	 * Broadcast a message to all clients but dont label it as being from any user or source. If
	 * the language filter is on then filter the message before it is sent out. This method is
	 * synchronized to make sure that messages broadcast one by one.
	 * 
	 * @param   message  The message to be sent.
	 */
	public synchronized void broadcast(String message)
	{
		if(langFilter)
			message = filter(message);

		for(int i = 0; i < clients.size(); i++)
			((HalClientThread)clients.get(i)).sendMessage(message);
	}

	/**
	 * Kick a user out of the chat room. They are disconnected but can reconnect if they wish.
	 * 
	 * @param   user   The name of the user to be kicked.
	 */
	public void kick(String user)
	{
		synchronized(clients)
		{
			for(int i = 0; i < clients.size(); i++)
			{
				if(((HalClientThread)clients.get(i)).getUserId().toLowerCase().equals(user.toLowerCase()))
				{
					((HalClientThread)clients.get(i)).sendMessage("* You have been kicked by the server.");
					((HalClientThread)clients.get(i)).disconnect();
					sendMessage("User  " + user + "  has been kicked from the server.");
					break;
				}

				sendMessage("Cannot find user: " + user);
			}
		}
	}

	/**
	 * Return a String containing a formatted list of all users connected.
	 * 
	 * @return   String   A formatted list of all users connected.
	 */
	public String listUsers()
	{
		String usersString = "There are " + clients.size() + " users connected: ";

		synchronized(clients)
		{
			for(int i = 0; i < clients.size(); i++)
			{
				if(i > 0)
					usersString += ", ";

				usersString += ((HalClientThread)clients.get(i)).getUserId();
			}
		}

		return usersString;
	}

	/**
	 * Read the message of the day file. Whatever is contained inside this file will
	 * be posted for users to see right as they log in.
	 * 
	 * @param  motdString  A String representing the location of the motd file.
	 */
	public void readMotdFile(String motdString)
	{
		BufferedReader in = null;
		String input = null;
		File motdFile = null;

		try
		{
			motdFile = new File(motdString);
			in = new BufferedReader(new FileReader(motdFile));

			for(int i = 0; (input = in.readLine()) != null; i++)
				motd.append(input);
		}
		catch(Exception e)
		{
			System.err.println("*** Error reading MOTD file: " + motdFile.getPath());
			System.err.println("*** Exception: " + e.getMessage());
		}
		finally
		{
			try
			{
				if(in != null)
				{
					in.close();
					in = null;
				}
			}
			catch(Exception e){}
		}
	}

	/**
	 * Read the filtered words file. There should be one line inside this file of comma
	 * delimited words. ie: "damn,crap,poop". Any words in this list will be filtered
	 * out of all messages during chatting.
	 * 
	 * @param  filterString  A String representing the location of the filter file.
	 */
	public void readFilterFile(String filterString)
	{
		BufferedReader in = null;
		String input = null;
		File filterFile = null;

		try
		{
			filterFile = new File(filterString);
			in = new BufferedReader(new FileReader(filterFile));

			input = in.readLine();
			filteredWords = input.split(",");

			for(int i = 0; i < filteredWords.length; i++)
				filteredWords[i] = filteredWords[i].trim();
		}
		catch(Exception e)
		{
			System.err.println("*** Error reading filter file: " + filterFile.getPath());
			System.err.println("*** Exception: " + e.getMessage());
		}
		finally
		{
			try
			{
				if(in != null)
				{
					in.close();
					in = null;
				}
			}
			catch(Exception e){}
		}

		langFilter = true;
	}

	/**
	 * Disconnect all clients. This method is called right before the server shuts down
	 * to try and ensure that all clients are notified of the shutdown and get a clean
	 * disconnection.
	 */
	public void disconnectClients()
	{
		//send a disconnect message to all clients that are connected
		try
		{
			synchronized(clients)
			{
				while(clients.size() > 0)
				{
					((HalClientThread)clients.get(0)).sendMessage("* Server is shutting down! *");
					((HalClientThread)clients.get(0)).disconnect();
					clients.trimToSize();
				}
			}
		}
		catch(Exception e)
		{
			System.err.println("*** Exception: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Shuts down the server. Disconnects all users and stops the Listener thread.
	 */
	public void shutdown()
	{
		disconnectClients();

		//shutdown local input stream
		try
		{
			if(in != null)
			{
				in.close();
				in = null;
			}
		}
		catch(Exception e){}
		
		//shutdown server if it is listening
		try
		{
			if(listener != null)
			{
				listener.halt();
				listener = null;
			}
		}
		catch(Exception e){}
	}

	/**
	 * Checks messages for words that need to be filtered. Words that are filtered
	 * are listed in the filter file that is specified by FILTER_FILE.
	 * 
	 * @param  message  Unfiltered message.
	 * @param  String   Filtered message.
	 */
	public synchronized String filter(String message)
	{
		for(int i = 0; i < filteredWords.length; i++)
		{
			if(message.toLowerCase().indexOf(filteredWords[i]) != -1)
			{
				String blockString = "";
				for(int j = 0; j < filteredWords[i].length(); j++)
					blockString += "*";

				message = message.replaceAll(filteredWords[i].toLowerCase(), blockString);	
			}
		}

		return message;
	}

	/**
	 * Outputs a message to the local server screen.
	 * 
	 * @param  message  Message to be displayed locally.
	 */
	public synchronized void sendMessage(String message)
	{
		System.out.println(message);
	}
}