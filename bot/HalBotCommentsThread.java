import java.util.*;

/**
 * This thread is called from the HalBot class. The HalBotCommentsThread
 * counts down the specified interval and outputs a comment from the comments 
 * Vector each time. ie: If the specified interval is 45 seconds then every 45
 * seconds a comment from the comments Vector will be outputted. It will start
 * at the beginning of the Vector and output the comments in order. Upon reaching
 * the end of the Vector, it will start over again from the beginning.
 * 
 * @author  Evan Lennick <eml0300@mail.widener.edu>
 * @version 1.0
 */
public class HalBotCommentsThread extends Thread
{
	HalBot bot = null;
	Vector comments = null;
	int delay;

	/**
	 * The class constructor.
	 * 
	 * @param  bot      The HalBot class that started this thread.
	 * @param  delay    The delay between outputting comments.
	 * @param  comments The Vector containing the comments to be outputted.
	 */
	HalBotCommentsThread(HalBot bot, int delay, Vector comments)
	{
		this.bot = bot;
		this.delay = delay;
		this.comments = comments;
	}

	/**
	 * The main process of this class. Waits for the interval amount of time
	 * and then outputs a comment. Continues as long as the bot is still
	 * connected.
	 */
	public void run()
	{
		try
		{
			Thread.sleep(delay * 1000);
		}
		catch(Exception e){}

		for(int i = 0; bot.connected; i++)
		{
			try
			{
				if(i >= comments.size())
					i = 0;

				bot.sendMessage((String)comments.get(i));
			}
			catch(Exception e)
			{
				System.err.println("*** Error sending timed comment.");
				System.err.println("*** Exception: " + e.getMessage());
				e.printStackTrace();
			}
			finally
			{
				try
				{
					Thread.sleep(delay * 1000);
				}
				catch(Exception e){}
			}
		}
	}
}