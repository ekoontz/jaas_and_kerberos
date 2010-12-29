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

  // message queue inbox table
  ConcurrentHashMap<SelectionKey,LinkedList<String>> inbox;

  AtomicInteger clientSerialNum;

  // send a message to all clients (except sender, if non-null).
  private void Broadcast(String message, SelectionKey sender) {
    // If sender is supplied, caller doesn't want a client to get 
    // a message from itself. 
    for (List<String> each : inbox.values()) {
      if ((sender == null)
          ||
          (each != inbox.get(sender))) { 
        each.add(message);
      }
    }
  }

  protected void WriteToClient(SelectionKey sk, AtomicInteger clientSerialNum) {

    // put sk on the read queue so that the Write worker(s) 
    // can see it.
    
    // initialize client nickname if necessary.
    if (clientNick.get(sk) == null) {
      clientNick.put(sk,"client #"+clientSerialNum.incrementAndGet());
    }
    
    // initialize message queue for this client if necessary.
    if (inbox.get(sk) == null) {
      inbox.put(sk,new LinkedList<String>());
    }
    
    // Send each message in this client's inbox queue to the client.
    // NoSuchElementException will be thrown when queue is emptied.
    try {
      while(true) {
        String messageForClient = inbox.get(sk).removeFirst().trim() + "\n";
        
        ByteBuffer writeBuffer = ByteBuffer.allocate(8192);
        writeBuffer.clear();
        
        System.out.println("writing " + messageForClient.getBytes().length + " bytes to client: " + clientNick.get(sk) + "\n");
        
        writeBuffer.put(messageForClient.getBytes(),0,Math.min(messageForClient.getBytes().length,8192));
        writeBuffer.flip();
        try {
          ((SocketChannel)sk.channel()).write(writeBuffer);
        }
        catch (IOException e) {
          System.err.println("IOException when trying to write: closing this client.");
          e.printStackTrace();
          clientNick.remove(sk);
          sk.cancel();
          try {
            sk.channel().close();
          }
          catch (IOException ioe) {
            System.err.println("IoException trying to close socket.");
            ioe.printStackTrace();
          }
        }
      }
    }
    catch (NoSuchElementException e) {
      // Finished writing to this client: no more messages in its inbox.
    }
  }

  protected void ReadFromClient(SelectionKey sk) {
    ReadFromClientLowLevel(sk);
  }

  protected void ReadFromClientLowLevel(SelectionKey sk) {

    // initialize client nickname if necessary.
    if (clientNick.get(sk) == null) {
      clientNick.put(sk,"client #"+clientSerialNum.incrementAndGet());
    }
    
    // initialize message queue if necessary.
    if (inbox.get(sk) == null) {
      inbox.put(sk,new LinkedList<String>());
    }
    
    System.out.println("Reading input from " + clientNick.get(sk));
    
    final SocketChannel socketChannel = (SocketChannel) sk.channel();
    int numRead = 0;
    // Attempt to read from the client.
    try {
      ByteBuffer readBuffer = ByteBuffer.allocate(8192);
      readBuffer.clear();
      numRead = socketChannel.read(readBuffer);
      if (numRead != -1) {
        readBuffer.flip();
        System.out.println("read: " + numRead + " bytes.");
        byte[] bytes = new byte[8192];
        readBuffer.get(bytes,0,numRead);
        Hexdump.hexdump(System.out,bytes,0,numRead);
        
        String clientMessage = new String(bytes);
        
        if (clientMessage.substring(0,1).equals("/")) {
          // Client used the "/" prefix to send the server a command: 
          // interpret the command.
          if (clientMessage.substring(0,6).equals("/nick ")) {
            String oldNick = clientNick.get(sk);
            String newNick = clientMessage.substring(6,clientMessage.length() - 6).trim();
            System.out.println("changing nickname to: " + newNick);
            clientNick.put(sk,newNick);
            Broadcast(oldNick + " is now known as " + newNick + ".\n",null);
          }
          
          if (clientMessage.substring(0,6).equals("/users")) {
            // Construct a human-readable list of users 
            // and send to client.
            String userList = "";
            userList = userList + "===Clients===";
            for (String nick: clientNick.values()) {
              userList = userList + "\n" + nick;
            }
            userList = userList + "\n\n";
            
            inbox.get(sk).add(userList);
          }                    
          
          if (clientMessage.substring(0,7).equals("/whoami")) {
            inbox.get(sk).add(new String("You are : " + clientNick.get(sk)));
          }                    
          
        }
        else {
          // Broadcast this client's message to all (other) clients:
          // that is all clients except sk.
          String nickName = clientNick.get(sk);
          String message = nickName + ": " + clientMessage + "\n";
          Broadcast(message,sk);
        }
        
        clientMessage = new String(bytes);
      }
    } catch (IOException e) {
      System.err.println("IOEXCEPTION: GIVING UP ON THIS CLIENT.");
      // The remote forcibly closed the connection, cancel
      // the selection key and close the channel.
      clientNick.remove(sk);
      sk.cancel();
      try {
        sk.channel().close();
      }
      catch (IOException ioe) {
        System.err.println("IoException trying to close socket.");
        ioe.printStackTrace();
      }
    }
    
    if (numRead == -1) {
      // Remote entity shut the socket down cleanly. Do the
      // same from our end and cancel the channel.
      
      System.out.println("Nothing left to read from client. Closing client connection: " + clientNick.get(sk));
      try {
        clientNick.remove(sk);
        sk.channel().close();
        
        // dump current client->context mapping to console.
        ShowClients();
        
      }
      catch (IOException ioe) {
        System.err.println("IoException trying to close socket.");
        ioe.printStackTrace();
      }                            
      sk.cancel();
    }
    
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

    // Initialize client inbox table.
    inbox = new ConcurrentHashMap<SelectionKey,LinkedList<String>>();

    clientSerialNum = new AtomicInteger(0);

    System.out.println("starting main listen loop..");
    while(true) {

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
          socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
          

        } else { 
          if (sk.isReadable()) {
            // put sk on the read queue so that the Read worker(s) 
            // can see it.
            ReadFromClient(sk);
          }
          
          try {
            if (sk.isWritable()) {
              WriteToClient(sk,clientSerialNum);
            }  
          }
          catch (CancelledKeyException e) {
            System.out.println("CancelledKeyException: maybe client closed.");
            
            // clean up data structures.
            System.out.println("Cancelled Key: closing client connection: " + clientNick.get(sk));
            try {
              clientNick.remove(sk);
              sk.channel().close();
              ShowClients();
            }
            catch (IOException ioe) {
              System.err.println("IOException trying to close socket.");
              ioe.printStackTrace();
            }
            sk.cancel();
          }
        }
      }
    }
  }
}

