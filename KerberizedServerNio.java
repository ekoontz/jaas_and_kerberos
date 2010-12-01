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

public class KerberizedServer {
  
  public static void main(String[] args) 
    throws IOException, GSSException {
    // 1. Prepare to authenticate with Kerberos.
    
    // 1.1. Oid mechanism = use Kerberos V5 as the security mechanism.
    Oid krb5Oid = new Oid( "1.2.840.113554.1.2.2");
    
    // 1.2 Set Kerberos Properties
    Properties props = new Properties();
    props.load( new FileInputStream( "server.properties"));
    System.setProperty( "sun.security.krb5.debug", "true");
    System.setProperty( "java.security.auth.login.config", "./jaas.conf");
    System.setProperty( "javax.security.auth.useSubjectCredsOnly", "true");
    String password = props.getProperty( "service.password");
    
    // 2. Login to the KDC.
    LoginContext loginCtx = null;
    // "KerberizedServer" refers to a section of the JAAS configuration in the jaas.conf file.
    Subject subject = null;
    try {
      loginCtx = new LoginContext( "KerberizedServer",
                                   new LoginCallbackHandler( password));
      loginCtx.login();
      subject = loginCtx.getSubject();
    }
    catch (LoginException e) {
      System.err.println("Login failure : " + e);
      System.exit(-1);
    }
    // Obtain the command-line arguments and parse the port number
    
    if (args.length != 1) {
      System.err.println("Usage: java <options> KerberizedServer <localPort>");
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

    GSSContext clientContext = null;

    // selection key => context map.
    final HashMap<SelectionKey,GSSContext> clientToContext = new HashMap<SelectionKey,GSSContext>();

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
        
        // Check what event is available and deal with it
        if (sk.isAcceptable()) {
          System.out.println("key is acceptable.");

          // For an accept to be pending the channel must be a server socket channel.
          ServerSocketChannel serverSocketChannel = (ServerSocketChannel) sk.channel();
          
          // Accept the connection and make it non-blocking
          SocketChannel socketChannel = serverSocketChannel.accept();
          socketChannel.configureBlocking(false);
          
          // Register the new SocketChannel with our Selector, indicating
          // we'd like to be notified when there's data waiting to be read
          socketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        } else if (sk.isReadable()) {
          System.out.println("reading context from channel.");
          final SocketChannel socketChannel = (SocketChannel) sk.channel();
          
          clientContext =
            Subject.doAs( subject, new PrivilegedAction<GSSContext>() {
                public GSSContext run() {
                  try {
                    GSSManager manager = GSSManager.getInstance();
                    GSSContext context = manager.createContext( (GSSCredential) null);
                    while (!context.isEstablished()) {
                      System.out.println("KerberizedServer: context not yet established: accepting from client.");
                      
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
                          context.acceptSecContext(bytes,0,numRead);
                        }
                      } catch (IOException e) {
                        System.err.println("IOEXCEPTION: GIVING UP ON THIS CLIENT.");
                        // The remote forcibly closed the connection, cancel
                        // the selection key and close the channel.
                        clientToContext.remove(sk);
                        sk.cancel();
                        try {
                          sk.channel().close();
                        }
                        catch (IOException ioe) {
                          System.err.println("IoException trying to close socket.");
                          ioe.printStackTrace();
                        }
                        return null;
                      }
                      
                      if (numRead == -1) {
                        // Remote entity shut the socket down cleanly. Do the
                        // same from our end and cancel the channel.
                        System.out.println("removing key from clientToContext.");
                        clientToContext.remove(sk);
                        try {
                          sk.channel().close();
                        }
                        catch (IOException ioe) {
                          System.err.println("IoException trying to close socket.");
                          ioe.printStackTrace();
                        }                            
                        sk.cancel();
                        return null;
                      }
                    }
                    System.out.println("returning context now.");
                    return context;
                  }
                  catch (GSSException e) {
                    System.err.println("GSS EXCEPTION: GIVING UP ON THIS CLIENT.");
                    e.printStackTrace();
                    clientToContext.remove(sk);
                    try {
                      sk.channel().close();
                    }
                    catch (IOException ioe) {
                      System.err.println("IoException trying to close socket.");
                      ioe.printStackTrace();
                    }
                    sk.cancel();
                    return null;
                  }
                }
              }
              );
          System.out.println("done with client context-acceptance.");
          if (clientContext != null) {
            clientToContext.put(sk,clientContext);
            System.out.println("KerberizedServer: Client authenticated: (principal: " + clientContext.getSrcName() + ")");
            // ..conduct business with client since it's authenticated and optionally encrypted too..
            
            
          }
          
          // dump current client->context mapping to console.
          System.out.println("===<current clients>===");
          for (SelectionKey each : clientToContext.keySet()) {
            GSSContext eachContext = null;
            if ((eachContext = clientToContext.get(each)) != null) {
              System.out.println("client principal: " + eachContext.getSrcName());
            }
          }
          System.out.println("===</current clients>===");
        } else if (sk.isWritable()) {
          //    .. write to client ..
        }
      }
    }

  }

}
