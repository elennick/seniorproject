import java.io.*;
import java.net.*;
import java.util.*;

/**
 * This class is intended to be a very simple AI bot that will reside
 * in the Hal chat server. It reads a config file to send in as a parameter
 * and that config file contains the locations of other config files, as
 * well as settings for the bot.
 * <p><p>
 * 
 * The bot will output random comments at an interval specified by the 
 * "CommentDelay" parameter in the config file. It will output the lines
 * written in the comments file specified by the "CommentsFile" parameter.
 * It will respond to keywords from the keywords file specified by the
 * "KeywordsFile" parameter. Lastly it will connect to the server specified
 * by the "ServerIp" and "ServerPort" parameters.
 * 
 * @author  Evan Lennick <eml0300@mail.widener.edu>
 * @version 1.0
 */
public class HalBot extends Thread
{
	//Constants
	private final String PROGRAM_NAME = "HalBot";

	//Globals
	protected boolean connected = false;
	private Socket socket = null;
	private BufferedReader in = null;
	private PrintWriter out = null;

	/**
	 * Start the bot thread. Pass in the arguements to the start() method.
	 * 
	 * @param  args[0]  Location of the config file.
	 */
	public static void main(String[] args)
	{
		HalBot bot = new HalBot();
		bot.start(args);
	}

	/**
	 * @param  args[0]  Location of the config file.
	 */
	public void start(String[] args)
	{
		//Read config file
		Properties configs = new Properties();
		String botName = null;
		String serverIp = null;
		int serverPort = 4545;
		File commentsFile = null;
		int commentDelay = 30;
		File keywordsFile = null;

		try
		{
			configs = readConfigFile(args[0]);

			botName = configs.getProperty("BotName");
			serverIp = configs.getProperty("ServerIp");
			serverPort = Integer.parseInt(configs.getProperty("ServerPort"));
			commentsFile = new File(configs.getProperty("CommentsFile"));
			commentDelay = Integer.parseInt(configs.getProperty("CommentDelay"));
			keywordsFile = new File(configs.getProperty("KeywordsFile"));
		}
		catch(ArrayIndexOutOfBoundsException aioobe)
		{
			System.err.println("*** Correct usage: java " + PROGRAM_NAME + " <config file>");
			cleanupAndExit();
		}
		catch(Exception e)
		{
			System.err.println("*** Error parsing config values. Please check config file for errors.");
			System.err.println("*** Exception: " + e.getMessage());
			e.printStackTrace();
			cleanupAndExit();
		}

		//Read keyword file
		Vector keywords = new Vector();
		keywords = readKeywordFile(keywordsFile);

		//Read comments file
		Vector comments = new Vector();
		comments = readCommentsFile(commentsFile);

		//Connect to chat server
		connect(serverIp, serverPort);

		//Start comments thread
		HalBotCommentsThread commentsThread = new HalBotCommentsThread(this, commentDelay, comments);
		commentsThread.start();

		//Main loop
		String input;
		
		try
		{
			while((input = in.readLine()) != null)
				processInput(input, keywords, botName);
		}
		catch(Exception e)
		{
			System.err.println("*** Error during main process.");
			System.err.println("*** Exception: " + e.getMessage());
			cleanupAndExit();
		}
	}

	/**
	 * Process input from the chat room.
	 * 
	 * @param  input     The input to be processed.
	 * @param  keywords  The list of keywords to recognize and their responses to output.
	 * @param  botName   The name of the bot.
	 */
	private void processInput(String input, Vector keywords, String botName)
	{
		String name = null;
		String content = null;
		
		try
		{
			if(input.indexOf(":") != -1)
				name = input.substring(0, input.indexOf(":")).trim();
			else if(input.indexOf(" ") != -1)
				name = input.substring(0, input.indexOf(" ")).trim();

			if(name.equals(botName))
				return;
		}
		catch(Exception e){}

		System.out.println("Processing input: " + input);

		if(input.equalsIgnoreCase("Login:"))
			sendMessage(botName);

		try
		{
			for(int i = 0; i < keywords.size(); i++)
			{
				try
				{
					String currentLine = ((String)keywords.get(i));
					String[] words = currentLine.substring(0, currentLine.indexOf("=")).split(",");
					String response = currentLine.substring(currentLine.indexOf("=") + 1, currentLine.length()).trim();

					for(int j = 0; j < words.length; j++)
					{
						if(input.trim().toLowerCase().indexOf(words[j].trim().toLowerCase()) != -1)
						{
							String output = response.replaceAll("<NAME>", name);
							System.out.println("Sending output: " + output);
							sendMessage(output);
							return;
						}
					}
				}
				catch(Exception e)
				{
					System.err.println("*** Error parsing keywords. Please check keywords file for errors." + input);
					System.err.println("*** Exception: " + e.getMessage());
				}
			}
		}
		catch(Exception e)
		{
			System.err.println("*** Error processing input: " + input);
			System.err.println("*** Exception: " + e.getMessage());
		}
	}

	/**
	 * Read configuration file.
	 * 
	 * @param   configFile  The location of the configuration file.
	 * @return  Properties  A Properties() object containing all the config values read in.
	 */
	private Properties readConfigFile(String configFile)
	{
		FileInputStream configIn = null;
		Properties configProp = null;

		try
		{
			configIn = new FileInputStream(configFile);
			configProp = new Properties();
			configProp.load(configIn);
		}
		catch(FileNotFoundException fnfe)
		{
			System.err.println("*** Error locating config file: " + configFile);
			System.err.println("*** Exception: " + fnfe.getMessage());
			cleanupAndExit();
		}
		catch(Exception e)
		{
			System.err.println("*** Unable to read config file: " + configFile);
			System.err.println("*** Exception: " + e.getMessage());
			cleanupAndExit();
		}
		finally
		{
			try
			{
				configIn.close();
			}
			catch(Exception e){}
		}

		return configProp;
	}

	/**
	 * Reads the keywords file, stores its values in a Vector and returns that Vector.
	 * 
	 * @param   keywordsFile  The location of the keywords file that was specified in the config file.
	 * @return  Vector        A Vector containing all the keywords and their responses.
	 */
	private Vector readKeywordFile(File keywordsFile)
	{
		Vector keywords = new Vector();
		BufferedReader keywordsIn = null;
		String input = "";

		try
		{
			keywordsIn = new BufferedReader(new FileReader(keywordsFile));
		
			while((input = keywordsIn.readLine()) != null)
			{
				if(input.trim().startsWith("#") || input.trim().equals(""))
					continue;
				else
					keywords.add(input);
			}
		}
		catch(FileNotFoundException fnfe)
		{
			System.err.println("*** Unable to locate keywords file: " + keywordsFile);
			System.err.println("*** Exception: " + fnfe.getMessage());
			cleanupAndExit();
		}
		catch(Exception e)
		{
			System.err.println("*** Unable to read keywords file: " + keywordsFile);
			System.err.println("*** Exception: " + e.getMessage());
			e.printStackTrace();
			cleanupAndExit();
		}
		finally
		{
			try
			{
				keywordsIn.close();
			}
			catch(Exception e){}
		}

		return keywords;
	}

	/**
	 * Read comments file.
	 * 
	 * @param   commentsFile  The location of the comments file that was specified in the config file.
	 * @return  Vector        A Vector containing a list of the comments to be outputted.
	 */
	private Vector readCommentsFile(File commentsFile)
	{
		Vector comments = new Vector();
		BufferedReader commentsIn = null;
		String input = "";

		try
		{
			commentsIn = new BufferedReader(new FileReader(commentsFile));
		
			while((input = commentsIn.readLine()) != null)
				comments.add(input);
		}
		catch(FileNotFoundException fnfe)
		{
			System.err.println("*** Unable to locate comments file: " + commentsFile);
			System.err.println("*** Exception: " + fnfe.getMessage());
			cleanupAndExit();
		}
		catch(Exception e)
		{
			System.err.println("*** Unable to read comments file: " + commentsFile);
			System.err.println("*** Exception: " + e.getMessage());
			e.printStackTrace();
			cleanupAndExit();
		}
		finally
		{
			try
			{
				commentsIn.close();
			}
			catch(Exception e){}
		}

		return comments;
	}

	/**
	 * Connect to the chat server.
	 * 
	 * @param   ip       Ip address of the server to connect to.
	 * @param   port     Port number that server is listening on.
	 * @return  boolean  Whether or not the connection was successful.
	 */
	private boolean connect(String ip, int port)
	{
		if(connected)
		{
			System.out.println("You are already connected!");
			return true;
		}

		try
		{
			socket = new Socket(ip, port);
		}
		catch(ConnectException ce)
		{
			System.err.println("Unable to connect!");
			System.err.println("Exception: " + ce.getMessage());
			return false;
		}
		catch(Exception e)
		{
			System.err.println("Exception: " + e.getMessage());
			return false;
		}

		try
		{
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(socket.getOutputStream());
		}
		catch(Exception e)
		{
			System.err.println("Unable to open streams!");
			System.err.println("Exception: " + e.getMessage());
			return false;
		}

		connected = true;
		return true;
	}

	/**
	 * Disconnect from the chat server.
	 * 
	 * @return  boolean  Whether or not the disconnection was successful.
	 */
	private boolean disconnect()
	{
		connected = false;
		return true;
	}

	/**
	 * Send a message to the server.
	 * 
	 * @param  message  The message to be sent to the server.
	 */
	protected synchronized void sendMessage(String message)
	{
		try
		{
			out.println(message);
			out.flush();
		}
		catch(Exception e)
		{
			System.err.println("*** Error sending output: " + message);
			System.err.println("*** Exception: " + e.getMessage());
		}
	}

	/**
	 * Disconnect if connected and stop the bot.
	 */
	private void cleanupAndExit()
	{
		if(out != null)
			sendMessage("Goodbye!");

		if(connected)
			disconnect();

		System.exit(0);
	}
}