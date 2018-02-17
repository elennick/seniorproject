import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;

public class HalStream extends Thread
{
	public final String NEW_LINE = System.getProperty("line.separator");
	private HalClient client = null;
	private boolean listening = false;

	HalStream(HalClient client)
	{
		this.client = client;
	}

	public void run()
	{
		String input = null;
		
		listening = true;
		try
		{
			while(listening)
			{
				try
				{	
					input = (client.in.readLine()).trim();
				
					if(input != null && !input.trim().equals(""))
					{
						if(input.trim().startsWith("*"))
							client.display(input + NEW_LINE, Color.RED);
						else if(input.indexOf(":") != -1)
						{
							String name = input.substring(0, input.indexOf(":"));
							String message = input.substring(input.indexOf(":"), input.length());

							client.display(name, Color.BLUE);
							client.display(message + NEW_LINE, Color.BLACK);
						}
						else
							client.display(input + NEW_LINE, Color.BLACK);

						if(input.startsWith("Login:"))
						{
							String username = JOptionPane.showInputDialog("What name would you like to be known by?");
							client.out.println(username);
							client.out.flush();
						}
					}

					if(input.indexOf("There are") != -1 && input.indexOf("users connected") != -1)
						updateUserList(input);
					else if(input.indexOf("has entered the room") != -1)
						addUser(input);
					else if(input.indexOf("has left the room") != -1)
						removeUser(input);
				}
				catch(NullPointerException npe)
				{
					halt();
					return;
				}
				catch(SocketException se)
				{
					client.display("Connection interrupted!" + NEW_LINE, Color.RED);
					halt();
					return;
				}
				catch(Exception e)
				{
					e.printStackTrace();
					halt();
				}
			}
		}
		catch(Exception e)
		{
			return;
		}
	}

	public void halt()
	{
		client.out.println("/exit");
		
		try
		{
			if(client.out != null)
			{
				client.out.close();
				client.out = null;
			}
		}
		catch(Exception e){}

		try
		{
			if(client.in != null)
			{
				client.in.close();
				client.in = null;
			}
		}
		catch(Exception e){}

		try
		{
			if(client.socket != null)
			{
				client.socket.close();
				client.socket = null;
			}
		}
		catch(Exception e){}
		
		listening = false;
		client.connected = false;
	}

	private void updateUserList(String input)
	{
		client.userList = new Vector();
		String[] users = input.substring(input.indexOf(":") + 1, input.length()).split(",");
												
		for(int i = 0; i < users.length; i++)
			client.userList.addElement(users[i].trim());

		client.nameList.setListData(client.userList);
	}

	private void addUser(String input)
	{
		String name = input.substring(0, input.indexOf(" ")).trim();

		if(!client.userList.contains(name))
		{
			client.userList.addElement(name);
			client.nameList.setListData(client.userList);
		}
	}

	private void removeUser(String input)
	{
		String name = input.substring(0, input.indexOf(" ")).trim();

		client.userList.remove(name);
		client.nameList.setListData(client.userList);
	}
}