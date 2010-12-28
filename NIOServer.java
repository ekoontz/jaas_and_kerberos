import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.IOException;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

import java.security.PrivilegedAction;

import java.util.Iterator;
import java.util.HashMap;
import java.util.Properties;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;

public class NIOServer {
  // selection key => handler map (start with Integer; later use callbacks of some kind.
  static HashMap<SelectionKey,Integer> clientToHandler;
  
  public static void ShowClients() {
    System.out.println("===<current clients>===");
    for (SelectionKey each : clientToHandler.keySet()) {
      Integer eachHandler;
      if ((eachHandler = clientToHandler.get(each)) != null) {
        System.out.println("key : " + each  + " => client handler: " + eachHandler);
      }
    }
    System.out.println("===</current clients>===");
  }

  public static void main(String[] args) 
    throws IOException {
    // Obtain the command-line arguments and parse the port number
    
    if (args.length != 1) {
      System.err.println("Usage: java <options> NIOServer <localPort>");
      System.exit(-1);
    }
    
    // 3. Main Loop: handle connections from network clients.
    // 3.1. Startup service network connection.
    int localPort = Integer.parseInt(args[0]);

    // <1. NIO>
    Selector selector = SelectorProvider.provider().openSelector();
    
    // Create a new non-blocking server socket channel
    ServerSocketChannel serverChannel = ServerSocketChannel.open();
    serverChannel.configureBlocking(false);
    
    // Bind the server socket to the specified address and port
    InetSocketAddress isa = new InetSocketAddress("localhost",localPort);
    serverChannel.socket().bind(isa);
    
    // Register the server socket channel, indicating an interest in 
    // accepting new connections
    serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    // </1. NIO>

    // selection key => handler map (start with Integer; later use callbacks of some kind.
    clientToHandler = new HashMap<SelectionKey,Integer>();

    Integer clientSerialNum = 0;


    System.out.println("start main listen loop..");
    while(true) {

      selector.select();
      Iterator selectedKeys = selector.selectedKeys().iterator();
      while (selectedKeys.hasNext()) {
        final SelectionKey sk = (SelectionKey) selectedKeys.next();
        selectedKeys.remove();

        if (!sk.isValid()) {
          System.out.println("key is not valid; continuing.");
          continue;
        }
        
        // Check what event is available and deal with it.
        if (sk.isAcceptable()) {
          System.out.println("Accepting connection from client.");
          clientToHandler.put(sk,clientSerialNum++);

          ShowClients();

          // For an accept to be pending the channel must be a server socket channel.
          ServerSocketChannel serverSocketChannel = (ServerSocketChannel) sk.channel();

          
          // Accept the connection and make it non-blocking
          SocketChannel socketChannel = serverSocketChannel.accept();
          socketChannel.configureBlocking(false);
          
          // Register the new SocketChannel with our Selector, indicating
          // we'd like to be notified when there's data waiting to be read
          socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
          

        } else if (sk.isReadable()) {
          System.out.println("Reading input from client: " + sk + " : =>" + clientToHandler.get(sk));

          final SocketChannel socketChannel = (SocketChannel) sk.channel();

          ByteBuffer readBuffer = ByteBuffer.allocate(8192);
          readBuffer.clear();
          
          // Attempt to read off the channel
          int numRead = 0;
          try {
            numRead = socketChannel.read(readBuffer);
            if (numRead != -1) {
              readBuffer.flip();
              System.out.println("read: " + numRead + " bytes.");
              byte[] bytes = new byte[8192];
              readBuffer.get(bytes,0,numRead);
              Hexdump.hexdump(System.out,bytes,0,numRead);
              ShowClients();
            }
          } catch (IOException e) {
            System.err.println("IOEXCEPTION: GIVING UP ON THIS CLIENT.");
            // The remote forcibly closed the connection, cancel
            // the selection key and close the channel.
            clientToHandler.remove(sk);
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

            // dump current client->context mapping to console.
            ShowClients();

            System.out.println("Nothing left to read from client. Closing client connection: " + sk);
            try {
              clientToHandler.remove(sk);
              sk.channel().close();
            }
            catch (IOException ioe) {
              System.err.println("IoException trying to close socket.");
              ioe.printStackTrace();
            }                            
            sk.cancel();
          }
        }

      }
    }
  }
}

