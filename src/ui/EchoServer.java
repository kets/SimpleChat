package ui;
// This file contains material supporting section 3.7 of the textbook:
// "Object Oriented Software Engineering" and is issued under the open-source
// license found at www.lloseng.com 

import java.io.*;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Scanner;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;

import com.lloseng.ocsf.server.*;

import common.ChatIF;

/**
 * This class overrides some of the methods in the abstract 

superclass in order
 * to give more functionality to the server.
 * @author Dr Timothy C. Lethbridge
 * @author Dr Robert Lagani&egrave;re
 * @author Fran&ccedil;ois B&eacute;langer
 * @author Paul Holden
 * @version July 2000
 */

public class EchoServer extends ObservableServer
{
    // Instance variables **********************************************

    /**
     * The interface type variable. It allows the implementation of the display
     * method in the client.
     */
    ChatIF serverUI;

    /**
     * Hashtable to store client's username and password pairs
     */
    private Hashtable<String, String> accounts;

    /**
     * Hashtable to store channel information
     */
    private Hashtable<String, ArrayList<ConnectionToClient>> channels;

    // Class variables *************************************************

    /**
     * The default port to listen on.
     */
    final public static int DEFAULT_PORT = 5555;

    /**
     * The default account file
     */
    final private static String ACCOUNT_FILE = "accounts.txt";

    // Constructors ****************************************************

    /**
     * Constructs an instance of the echo server.
     * @param port The port number to connect on.
     * @param serverUI The interface type variable.
     */
    public EchoServer(int port, ChatIF serverUI)
    {
        super(port);
        this.serverUI = serverUI;
        channels = new Hashtable<String, ArrayList<ConnectionToClient>>();
        accounts = new Hashtable<String, String>();

        // import accounts from text file
        File accountFile = new File(ACCOUNT_FILE);
        try
        {
            Scanner input = new Scanner(new FileReader(accountFile));
            while (input.hasNextLine())
            {
                String[] nextAccount = input.nextLine().split(":");
                accounts.put(nextAccount[0], nextAccount[1]);
            }
            input.close();
        }
        catch (FileNotFoundException e)
        {

        }
    }

    // Instance methods ************************************************

    /**
     * This method handles any messages received from the client.
     * @param msg The message received from the client.
     * @param client The connection from which the message originated.
     */
    public void handleMessageFromClient(Object msg, ConnectionToClient client)
    {
        // if the client is in a channel
        if (client.getInfo("channel") != null)
        {
            // send message to channel members
            sendMessageToChannel((String) client.getInfo("channel"), msg,
                    (String) client.getInfo("loginid"), client);
            return;
        }

        String message = (String) msg;
        String[] line = message.split(" ");
        if (line[0].equals("#login"))
        {
            // if username does not exist, create new account
            if (!accounts.containsKey(line[1]))
            {
                accounts.put(line[1], line[2]);
            }

            // if the username/password does not match
            if (!accounts.get(line[1]).equalsIgnoreCase(line[2]))
            {
                try
                {
                    // display error, terminate client
                    client
                            .sendToClient("Invalid username/password...disconnecting");
                    client.close();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            else
            {

                System.out.println("Message received: " + msg + " from "
                        + client.getInfo("loginid"));
                client.setInfo("loginid", line[1]); // Save client's login id
                String loginMessage = client.getInfo("loginid")
                        + " has logged on.";
                System.out.println(loginMessage);
                this.sendToAllClients(loginMessage); // Notify clients that
                // this
                // client has logged on
            }
        }
        else if (line[0].equals("#private")) // if it is a private message
        {
            // get all connected clients
            Thread[] clientThreadList = getClientConnections();

            // iterate through all clients
            for (int i = 0; i < clientThreadList.length; i++)
            {
                try
                {
                    ConnectionToClient currClient = (ConnectionToClient) clientThreadList[i];

                    // if the login id matches the client
                    if (currClient.getInfo("loginid").equals(line[1]))
                    {
                        // send the message to this client
                        String privateMessage = "";
                        for (int j = 2; j < line.length; j++)
                        {
                            privateMessage += line[j] + " ";
                        }

                        client.sendToClient("To "
                                + currClient.getInfo("loginid") + ":> "
                                + privateMessage);
                        currClient.sendToClient("From "
                                + client.getInfo("loginid") + ":> "
                                + privateMessage);

                        if (currClient.getInfo("monitor") != null)
                        {
                            ((ConnectionToClient) currClient.getInfo("monitor"))
                                    .sendToClient("Message for: "
                                            + currClient.getInfo("loginid")
                                            + " from "
                                            + client.getInfo("loginid") + "> "
                                            + privateMessage);
                            
                            // store all messages being monitored
                            Stack<String> awayMessages = (Stack<String>) currClient.getInfo("awayMessages");
                            awayMessages.push("From " + client.getInfo("loginid") + ":> " + privateMessage);
                        }
                        break;
                    }
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }
        else if (line[0].equals("#create")) // if it is a create command
        {
            // if the channel already exists
            if (channels.containsKey(line[1]))
            {
                try
                {
                    client.sendToClient("Error. Channel name taken");
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            else
            {
                // create the channel
                ArrayList<ConnectionToClient> newChannel = new ArrayList<ConnectionToClient>();

                // add the client
                newChannel.add(client);
                client.setInfo("channel", line[1]);

                // add channel to hashtable
                channels.put(line[1], newChannel);

                try
                {
                    client.sendToClient("You've joined channel " + line[1]);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
        else if (line[0].equals("#join")) // if it is a join command
        {
            // if the client is already in a channel
            if (client.getInfo("channel") != null)
            {
                try
                {
                    client.sendToClient("Error. You are already in a channel");
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }

                return;
            }

            // if the channel does not exist
            if (!channels.containsKey(line[1]))
            {
                try
                {
                    client.sendToClient("Error. Channel does not exist");
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }

                return;
            }

            // get the channel
            ArrayList<ConnectionToClient> theChannel = channels.get(line[1]);

            // add the client
            theChannel.add(client);
            client.setInfo("channel", line[1]);

            try
            {
                client.sendToClient("You've successfully joined " + line[1]);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        else if (line[0].equals("#displayChannels")) // if it is a
        // displayChannels
        // command
        {
            // get all the channel names
            Set<String> channelNames = channels.keySet();

            // construct the list and send to the client
            String channelList = "Available Channels:\n";
            for (String s : channelNames)
            {
                channelList += s + "\n";
            }
            try
            {
                client.sendToClient(channelList);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        else if (line[0].equals("#select"))
        {
            // get all connected clients
            Thread[] clientThreadList = getClientConnections();

            for (int i = 0; i < clientThreadList.length; i++)
            {
                try
                {
                    ConnectionToClient currClient = (ConnectionToClient) clientThreadList[i];

                    // if the login id matches the client
                    if (currClient.getInfo("loginid").equals(line[1]))
                    {
                        // lock in client for monitoring
                        client.setInfo("monitor", currClient);
                        
                        // store messages when client is away
                        client.setInfo("awayMessages", new Stack<String>());
                        currClient.setInfo("monitee", client);
                        currClient
                                .sendToClient("**Congratulations!** \n You have been selected to be a message monitor by "
                                        + client.getInfo("loginid"));
                        break;
                    }
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }
        else if (line[0].equals("#back"))
        {
            ConnectionToClient monitee = (ConnectionToClient) client
                    .getInfo("monitor");
            try
            {
                monitee.sendToClient(client.getInfo("loginid") + " is back\n"
                        + "You are relieved of your monitoring duties");
                monitee.setInfo("monitee", null);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            client.setInfo("monitor", null);
        }
        else if (line[0].equals("#retrieve"))
        {
            Stack<String> awayMessages = (Stack<String>) client.getInfo("awayMessages");
            try
            {
                // all messages sent to user during the time user is away is forwarded to user
                client.sendToClient("====Start Away Messages====");
                for(String s: awayMessages)
                {
                    client.sendToClient(s);
                }
                client.sendToClient("====End Away Messages====");
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        else
        {
            System.out.println("Message received: " + msg + " from "
                    + client.getInfo("loginid"));
            String loginid = (String) client.getInfo("loginid");
            // Messages sent to clients prefixed by this cleint's login id
            this.sendToAllClients(loginid + " says: " + msg);
        }
    }

    /**
     * Sends a message to all the clients in a channel
     * @param channelName name of the channel
     * @param message message to send
     * @param loginid login id of the sender
     */
    private void sendMessageToChannel(String channelName, Object msg,
            String loginid, ConnectionToClient client)
    {
        String message = (String) msg;
        String[] line = message.split(" ");
        
        if (line[0].equals("#leave")) // if it is a leave command
        {
            // if the client is not in a channel
            if (client.getInfo("channel") == null)
            {
                try
                {
                    client.sendToClient("Error. You are not in a channel");
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }

                return;
            }

            // get the channel
            ArrayList<ConnectionToClient> theChannel = channels.get(channelName);

            // remove the client
            theChannel.remove(client);
            
            if(theChannel.size() == 0)
            {
                channels.remove(channelName);
            }
            
            client.setInfo("channel", null);

            try
            {
                client.sendToClient("You've left " + channelName);
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            // get the channel
            ArrayList<ConnectionToClient> theChannel = channels.get(channelName);

            // send to all clients in channel
            for (ConnectionToClient c : theChannel)
            {
                try
                {
                    c.sendToClient("Channel: " + channelName + "> " + loginid
                            + " says: " + message);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * This method overrides the one in the superclass. Called when the server
     * starts listening for connections.
     */
    protected void serverStarted()
    {
        System.out.println("Server listening for connections on port "
                + getPort());
    }

    /**
     * This method overrides the one in the superclass. Called when the server
     * stops listening for connections.
     */
    protected void serverStopped()
    {
        // save the accounts to file
        File accountFile = new File(ACCOUNT_FILE);
        try
        {
            PrintWriter output = new PrintWriter(accountFile);

            Set<String> accountList = accounts.keySet();

            for (String key : accountList)
            {
                output.println(key + ":" + accounts.get(key));
            }

            output.close();
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
        }
        System.out.println("Server has stopped listening for connections.");
    }

    /**
     * Overridden method called each time a new client connection is accepted.
     * @param client the connection connected to the client.
     */
    protected void clientConnected(ConnectionToClient client)
    {
        /*
         * // saves the client's description client.setInfo("client",
         * client.toString()); // displays message on server console when the
         * client connects System.out.println(client + " has logged on.");
         */
        try
        {
            client.sendToClient("");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        System.out
                .println("A new client is attempting to connect to the server.");
    }

    /**
     * Overridden method called each time a client disconnects. The client is
     * guarantee to be disconnected but the thread is still active until it is
     * asynchronously removed from the thread group.
     * @param client the connection with the client.
     */
    synchronized protected void clientDisconnected(ConnectionToClient client)
    {
        // if the client logging off is a monitor
        if (client.getInfo("monitee") != null)
        {
            // let the monitee know that their monitor is gone
            try
            {
                ((ConnectionToClient) client.getInfo("monitee"))
                        .sendToClient("Your monitor, "
                                + client.getInfo("loginid")
                                + ", has logged off");
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            ((ConnectionToClient) client.getInfo("monitee")).setInfo("monitee",
                    null);
        }
        // displays message on server console when the client disconnects
        System.out.println(client.getInfo("loginid") + " has logged off.");
    }
    
    /**
     * quit()
     */
    public void quit() throws IOException
    {
        close();

    }

    /**
     * closeConnection();
     */
    public void closeConnection() throws IOException
    {
        stopListening();
        close();

    }

    /**
     * startListening()
     */
    public void startListening() throws IOException
    {
        listen();
    }     
}
// End of EchoServer class
