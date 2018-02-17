import javax.swing.*;
import javax.swing.text.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.text.html.*;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * This is the client applet for the Hal chat program. It is the interface
 * that allows users on the internet to interact with the HalServer, HalBot
 * and other connected HalClient's. It is a java applet which means it
 * SHOULD work on almost any platform in a browser that supports Java.
 * <p><p>
 * 
 * NOTE: Of all components in the Hal chat system, the client is by far the
 * least polished and can still be considered in beta status. There was just
 * not nearly enough time before the senior project due date to implement all
 * the features i wanted and fix all the functionality already coded. Even
 * after the project is submitted I will attempt to continue improving this
 * client to a point where it is stable and reliable.
 * 
 * Incomplete features:
 * -> Hyperlinks cannot be clicked. Either make it so that the link opens the
 *    default OS browser or create a custom java applet to open hyperlinks.
 * -> The entire Edit menu is not implemented.
 * -> The Help->Help option is not implemented. Not sure what this option
 *    should display anyways.
 * -> Custom icon for applet has not been added. A small issue but adds to the
 *    polish and uniqueness of this applet.
 * 
 * Known issues:
 * -> Applet does not resize components correctly.
 * -> Disconnecting and disconnection messages are very flakey.
 *
 * @author  Evan Lennick <eml0300@mail.widener.edu>
 * @version 0.2 Beta
 */
public class HalClient extends JApplet
{
	//Constants
	public final String APPLET_NAME = "WUCS Chat";
	public final String VERSION = "0.2 Beta";
	public final String NEW_LINE = System.getProperty("line.separator");
	public final String INIT_STRING = "Client initialized! (v" + VERSION + ")" + NEW_LINE + NEW_LINE + "This applet is still in beta so please excuse any bugs or missing features you may encounter. If you find any bugs/issues or have any comments, please email this applets developer at <eml0300@mail.widener.edu>. Thanks!" + NEW_LINE + NEW_LINE;
	public final int FONT_SIZE = 13;
	public final Color DARK_GREEN = new Color(0, 150, 0);

	//Generic Globals
	protected boolean connected = false;
	protected BufferedReader in = null;
	protected PrintWriter out = null;
	protected Socket socket = null;
	private HalStream streamThread = null;
	protected Vector userList;
	private String ip = null;
	private int port = 4545;
	
	//Applet Globals
	private JFrame mainFrame;
	private JMenuBar menuBar;
	private JMenu connectionMenu, editMenu, helpMenu;
	private JMenuItem connectItem, disconnectItem, exitItem;
	private JMenuItem copyItem, pasteItem, findItem;
	private JMenuItem helpItem, aboutItem;
	private JTextPane textPane;
	private StyledDocument textDoc;
	private Style style;
	protected JList nameList;
	private JTextField textField;
	private JSplitPane horzSplitPane, vertSplitPane;
	private JScrollPane textScrollPane, nameScrollPane;
	
	/**
	 * Creates and displays the HalClient GUI.
	 */
	private void createGUI()
	{
		//main frame  *Reminder: add custom icon to the frame if there is time
		JFrame.setDefaultLookAndFeelDecorated(false);
		
		mainFrame = new JFrame(APPLET_NAME);
		mainFrame.setSize(640, 480);
		mainFrame.setLocationRelativeTo(null);
		
		//menu bar setup
		menuBar = new JMenuBar();
		
		//connection menu
		connectionMenu = new JMenu("Connection");
		connectionMenu.setMnemonic(KeyEvent.VK_C);
				
		connectItem = new JMenuItem("Connect");
		connectItem.setMnemonic(KeyEvent.VK_C);
		connectionMenu.add(connectItem);
				
		disconnectItem = new JMenuItem("Disconnect");
		disconnectItem.setMnemonic(KeyEvent.VK_D);
		connectionMenu.add(disconnectItem);
		
		connectionMenu.addSeparator();
		
		exitItem = new JMenuItem("Exit");
		exitItem.setMnemonic(KeyEvent.VK_X);
		connectionMenu.add(exitItem);
		
		menuBar.add(connectionMenu);
		
		//edit menu
		editMenu = new JMenu("Edit");
		editMenu.setMnemonic(KeyEvent.VK_E);
		
		copyItem = new JMenuItem("Copy");
		copyItem.setMnemonic(KeyEvent.VK_C);
		editMenu.add(copyItem);
		
		pasteItem = new JMenuItem("Paste");
		pasteItem.setMnemonic(KeyEvent.VK_P);
		editMenu.add(pasteItem);
		
		findItem = new JMenuItem("Find");
		findItem.setMnemonic(KeyEvent.VK_F);
		editMenu.add(findItem);
		
		menuBar.add(editMenu);
		
		//help menu
		helpMenu = new JMenu("Help");
		helpMenu.setMnemonic(KeyEvent.VK_H);
		menuBar.add(helpMenu);
		
		helpItem = new JMenuItem("Help");
		helpItem.setMnemonic(KeyEvent.VK_H);
		helpMenu.add(helpItem);
		
		aboutItem = new JMenuItem("About");
		aboutItem.setMnemonic(KeyEvent.VK_A);
		helpMenu.add(aboutItem);
		
		mainFrame.setJMenuBar(menuBar);
		
		//set up output text pane
		textPane = new JTextPane();
		textPane.setContentType("text/plain");
		textPane.setEditable(false);
		textDoc = (StyledDocument)textPane.getDocument();
		
		textScrollPane = new JScrollPane(textPane, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		
		//set up text input field
		textField = new JTextField();
		
		//set up name list area
		nameList = new JList();
		nameScrollPane = new JScrollPane(nameList, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		
		//set up horizontal split pane
		horzSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, textScrollPane, nameScrollPane);
		horzSplitPane.setDividerLocation(500);
		mainFrame.getContentPane().add(horzSplitPane, BorderLayout.CENTER);
				
		//set up veritcal split pane
		vertSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, horzSplitPane, textField);
		vertSplitPane.setDividerLocation(370);
		mainFrame.getContentPane().add(vertSplitPane, BorderLayout.CENTER);

		//done setting up GUI so set it to be visible
		mainFrame.setVisible(true);
		
		display(INIT_STRING, DARK_GREEN);
	}
	
	/**
	 * Adds and defines all event listeners for applet components.
	 */
	private void addListeners()
	{
		//actionlistener for the user text input box
		textField.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				if(textField.getText().trim().equalsIgnoreCase("/help"))
					help();
				else if(textField.getText().trim().equalsIgnoreCase("/quit"))
					destroy();
				else if(textField.getText().toLowerCase().startsWith("/connect"))
					connect();
				else if(textField.getText().trim().equalsIgnoreCase("/disconnect"))
					disconnect();
				else
				{
					try
					{
						out.println(textField.getText());
						out.flush();
					}
					catch(Exception e2)
					{
						display("Error sending data!" + NEW_LINE, Color.RED);
					}
				}
				
				textField.setText("");
			}
		});
		
		//actionlistener for the Connection->Connect option
		connectItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				connect();
			}
		});
		
		//actionlistener for the Connection->Disconnect option
		disconnectItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				disconnect();
			}
		});

		//actionlistener for the Connection->Exit option
		exitItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				destroy();
			}
		});
		
		//actionlistener for the Help->Help option
		helpItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				help();
			}
		});

		//actionlistener for the Help->About option
		aboutItem.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
				String aboutText = "Author: Evan Lennick\nVersion: " + VERSION;
				JOptionPane.showMessageDialog(mainFrame, aboutText, "About " + APPLET_NAME, JOptionPane.INFORMATION_MESSAGE);
			}
		});
	}
	
	/**
	 * Show help dialogue.
	 */
	private void help()
	{
		String helpText = "- Available client commands: \"/connect, /disconnect, /help, /quit\"" + NEW_LINE
			+ "- If you are connected, just type \"/help\" for a list of server commands." + NEW_LINE
			+ "- For help from the WUCS-Bot just say \"help\" out loud in the chat room.";
		JOptionPane.showMessageDialog(mainFrame, helpText, APPLET_NAME + " Help", JOptionPane.INFORMATION_MESSAGE);
	}

	/**
	 * Connect to the HalServer.
	 */
	private void connect()
	{
		try
		{
			display("Connecting...", Color.BLACK);
							
			if(connected)
			{
				display("You are already connected!" + NEW_LINE, Color.RED);
				return;
			}
	
			try
			{
				socket = new Socket(ip, port);
			}
			catch(ConnectException ce)
			{
				display("Unable to connect!" + NEW_LINE, Color.RED);
				return;
			}

			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			out = new PrintWriter(socket.getOutputStream());

			display("Connection established!" + NEW_LINE, Color.BLACK);

			connected = true;

			streamThread = new HalStream(this);
			streamThread.start();
		}
		catch(Exception e)
		{
			display("*** Exception: " + e.getMessage() + NEW_LINE, Color.RED);
		}
	}
	
	/**
	 * Disconnect from the HalServer.
	 */
	protected synchronized void disconnect()
	{
		try
		{
			streamThread.halt();
		}
		catch(Exception e){}
		
		streamThread = null;
		connected = false;

		display("Disconnected." + NEW_LINE, Color.RED);
	}
	
	public synchronized void display(String message, Color textColor)
	{
		try
		{
			style = textDoc.addStyle("Current", null);
			StyleConstants.setFontSize(style, FONT_SIZE);
			StyleConstants.setBackground(style, Color.white);
			StyleConstants.setForeground(style, textColor);

			textDoc.insertString(textDoc.getLength(), message, style);
			textPane.setCaretPosition(textPane.getDocument().getLength());
		}
		catch(Exception e)
		{
			System.err.println("*** Error displaying text: " + message);
			System.err.println("*** Exception: " + e.getMessage());
		}
	}
	
	/**
	 * Initialize the program.
	 */
	public void init()
	{
		javax.swing.SwingUtilities.invokeLater(new Runnable() 
		{
			public void run() 
			{
				//get the server ip from the html parameter "ip"
				try
				{
					ip = getParameter("ip");
				}
				catch(Exception e)
				{
					System.err.println("*** IP parameter not found!");
					System.err.println("*** Exception: " + e.getMessage());
					return;
				}

				createGUI();
				addListeners();
				connect();
			}
		});
	}
	
	/**
	 * Clean up and end program.
	 */
	public void destroy()
	{
		if(connected)
			disconnect();
					
		mainFrame.dispose();
	}
}