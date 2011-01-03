import java.io.IOException;
import java.net.InetSocketAddress;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.channels.CancelledKeyException;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.LinkedList;
import java.util.NoSuchElementException;

public class NIOServer {

  // nickname table
  ConcurrentHashMap<SelectionKey,String> clientNick;

  AtomicInteger clientSerialNum;

  // send a message to a client.
  protected void Send(String message, SelectionKey recipient) {
    System.out.println("writing message: " + message + " to " + recipient);

    WriteToClient(recipient,message + "\n");
  }

  // send a message to all clients from the system.
  protected void BroadcastSystem(String message) {
    Broadcast("** " + message,null);
  }

  // send a message to all clients (except sender, if non-null).
  protected void Broadcast(String message, SelectionKey sender) {
    // If sender is supplied, sender will not receive a
    // message from itself. 
    for (SelectionKey recipient: clientNick.keySet()) {
      if ((sender == null)
          ||
          (recipient != sender)) {
        System.out.println("Send(): " + message + " to " + recipient);
        Send(message,recipient);
      }
    }
  }

  protected void WriteToClient(SelectionKey sk, String message) {
    WriteToClientByNetwork(sk,message);
  }

  protected void WriteToClientByNetwork(SelectionKey sk, String message) {
    if (clientNick.get(sk) == null) {
      System.out.println("WriteToClientByNetwork(): REFUSING TO WRITE TO UNREGISTERED KEY: " + sk);
      return;
    }
    
    String messageForClient = message;
    ByteBuffer writeBuffer = ByteBuffer.allocate(8192);
    writeBuffer.clear();
    System.out.println("writing " + messageForClient.getBytes().length + " bytes to client: " + clientNick.get(sk) + "\n");
    Hexdump.hexdump(System.out,messageForClient.getBytes(),0,messageForClient.getBytes().length);
    
    writeBuffer.put(messageForClient.getBytes(),0,Math.min(messageForClient.getBytes().length,8192));
    writeBuffer.flip();
    try {
      ((SocketChannel)sk.channel()).write(writeBuffer);
    }
    catch (IOException e) {
      System.err.println("WriteToClientByNetwork(): IOException when trying to write: closing this client.");
      CancelClient(sk);
    }
  }

  protected void ReadFromClient(SelectionKey sk) {
    String message = ReadFromClientByNetwork(sk);
    if (message != null) {
      ProcessClientMessage(sk,message);
    }
  }

  protected byte[] ReadBytesFromClientByNetwork(SelectionKey sk) {
    // initialize client nickname if necessary.    
    System.out.println("Reading input from " + clientNick.get(sk));

    if (clientNick.get(sk) == null) {
      clientNick.put(sk,"client #"+clientSerialNum.incrementAndGet());
      BroadcastSystem(clientNick.get(sk) + " joined the chat.");
    }
    
    final SocketChannel socketChannel = (SocketChannel) sk.channel();
    int numRead = 0;
    // Attempt to read from the client.
    try {
      ByteBuffer readBuffer = ByteBuffer.allocate(8192);
      readBuffer.clear();
      numRead = socketChannel.read(readBuffer);
      if (numRead != -1) {
        readBuffer.flip();
        System.out.println("ReadBytesFromClientByNetwork() read: " + numRead + " bytes.");
        byte[] bytes = new byte[numRead+1];
        readBuffer.get(bytes,0,numRead);
        Hexdump.hexdump(System.out,bytes,0,numRead);
        return bytes;
      }
    }
    catch (IOException e) {
      System.err.println("ReadFromClientByNetwork() Exception: Canceling this client.");
      // The remote closed the connection. Cancel
      // the selection key and close the channel.
      CancelClient(sk);
    }
    return null;
  }

  protected String ReadFromClientByNetwork(SelectionKey sk) {
    byte[] bytes = ReadBytesFromClientByNetwork(sk);
    if (bytes == null) {
      System.out.println("key is not valid: assuming client hung up.");
      CancelClient(sk);
      return null;
    }
    String clientMessage = new String(bytes);
    return clientMessage;
  }

  protected void CancelClient(SelectionKey sk) {
    String nickname = clientNick.get(sk);
    if (nickname != null) {
      clientNick.remove(sk);
      sk.cancel();
      try {
        sk.channel().close();
      }
      catch (IOException ioe) {
        System.err.println("IoException trying to close socket.");
        ioe.printStackTrace();
      }
      BroadcastSystem(nickname + " hung up.");
    }
  }

  // return false if a non-recognized command was received; true otherwise.
  protected boolean ProcessClientMessage(SelectionKey sk,String clientMessage) {
    if (clientMessage.substring(0,1).equals("/")) {
      // Client used the "/" prefix to send the server a command: 
      // interpret the command.
      if (clientMessage.substring(0,6).equals("/nick ")) {
        String oldNick = clientNick.get(sk);
        String newNick = clientMessage.substring(6,clientMessage.length()).trim();
        System.out.println("changing nickname to: " + newNick);
        clientNick.put(sk,newNick);
        BroadcastSystem(oldNick + " is now known as " + newNick + ".");
        return true;
      }
      
      if (clientMessage.substring(0,6).equals("/users")) {
        // Construct a human-readable list of users 
        // and send to client.
        String userList = "";
        userList = userList + "===Clients===";
        for (String nick: clientNick.values()) {
          userList = userList + "\n" + nick;
          if (clientNick.get(sk).equals(nick)) {
            userList = userList + " <= you";
          }
        }
        Send(userList,sk);
        return true;
      }

      if (clientMessage.substring(0,5).equals("/quit")) {
        String nick = clientNick.get(sk);
        Send("goodbye, " + nick + ".",sk);
        BroadcastSystem(nick + " has left the chatroom.");
        CancelClient(sk);
        return true;
      }

      if (clientMessage.substring(0,5).equals("/help")) {
        Send(ShowCommands(),sk);
        return true;
      }

      if (clientMessage.substring(0,7).equals("/whoami")) {
        Send("You are : " + clientNick.get(sk),sk);
        return true;
      }
      // unmatched command.
      return false;
    }
    else {
      // Broadcast this client's message to all (other) clients:
      // that is all clients except sk.
      String nickName = clientNick.get(sk);
      String message = nickName + ": " + clientMessage + "\n";
      System.out.println("broadcasting message: " + message);
      Broadcast(message.trim(),sk);
      return true;
    }
  }

  public String ShowCommands() {
    String retval = "\n";
    retval += "All commands are prefixed with a forward slash (/).\n";
    retval += "/help - show all commands.\n";
    retval += "/quit - disconnect from server.\n";
    retval += "/users - show all connected clients.\n";
    retval += "/nick <name> - change your nick to <name>.\n";
    return retval;
  }

  public void ShowClients() {
    System.out.println("Connected clients: " + clientNick.size());
  }

  public static void main(String[] args) 
    throws IOException {
    // Obtain the command-line arguments and parse the port number
    
    if (args.length != 1) {
      System.err.println("Usage: java <options> NIOServer <localPort>");
      System.exit(-1);
    }
    
    int localPort = Integer.parseInt(args[0]);

    NIOServer instance = new NIOServer();
    try {
      instance.run(localPort);
    }
    catch (IOException e) {
      throw e;
    }
    
  }
  
  public void run(int localPort) 
    throws IOException {
    // <NIO Setup>
    Selector selector = SelectorProvider.provider().openSelector();
    
    // Create a new non-blocking server socket channel
    ServerSocketChannel serverChannel = ServerSocketChannel.open();
    serverChannel.configureBlocking(false);
    
    // Bind the server socket to the specified address and port
    InetSocketAddress isa = new InetSocketAddress("192.168.56.1",localPort);
    serverChannel.socket().bind(isa);
    
    // Register the server socket channel, indicating an interest in 
    // accepting new connections
    serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    // </NIO Setup>

    // Initialize client nickname table.
    clientNick = new ConcurrentHashMap<SelectionKey,String>();

    clientSerialNum = new AtomicInteger(0);

    System.out.println("starting main listen loop.");
    while(true) {

      try {
        //        System.out.println("NIOServer:waiting on select().");
        selector.select();

        Iterator selectedKeys = selector.selectedKeys().iterator();
        while (selectedKeys.hasNext()) {
          
          final SelectionKey sk = (SelectionKey) selectedKeys.next();
          selectedKeys.remove();
          
          //        System.out.println("iterating through keys: current key: " + sk.toString());
          
          if (!sk.isValid()) {
            System.out.println("key is not valid; continuing.");
            continue;
          }
          
          // Check what event is available and deal with it.
          
          if (sk.isAcceptable()) {
            System.out.println("Accepting connection from client with accept key : " + sk);
            
            // For an accept to be pending the channel must be a server socket channel.
            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) sk.channel();
            
            // Accept the connection and make it non-blocking
            SocketChannel socketChannel = serverSocketChannel.accept();
            socketChannel.configureBlocking(false);
            
            // Register the new SocketChannel with our Selector, indicating
            // we'd like to be notified when there's data waiting to be read
            socketChannel.register(selector, SelectionKey.OP_READ);
            
          } else { 
            if (sk.isReadable()) {
              ReadFromClient(sk);
            }
          }
        }
      }
      catch (CancelledKeyException e) {
        System.err.println("KEY CANCELLED.");
        continue;
      }
    }
  }
}

