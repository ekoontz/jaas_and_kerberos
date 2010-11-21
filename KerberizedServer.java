import org.ietf.jgss.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.Properties;
import java.security.PrivilegedAction;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.*;

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


    // 2. 'old' IO
    ServerSocket ss = new ServerSocket(localPort);
    
    while (true) {      
      System.out.println("KerberizedServer: Waiting for client connection...");

      // 3.2 Receive a client connection.
      Socket socket = ss.accept();
      final DataInputStream inStream =
        new DataInputStream(socket.getInputStream());
      final DataOutputStream outStream = 
        new DataOutputStream(socket.getOutputStream());
      
      System.out.println("KerberizedServer: Got connection from client "
                         + socket.getInetAddress());
      
      // 3.3 get client's security context
      GSSContext clientContext =
        Subject.doAs( subject, new PrivilegedAction<GSSContext>() {
            public GSSContext run() {
              try {
                GSSManager manager = GSSManager.getInstance();
                GSSContext context = manager.createContext( (GSSCredential) null);
                while (!context.isEstablished()) {
                  System.out.println("KerberizedServer: context not yet established: accepting from client.");
                  context.acceptSecContext(inStream,outStream);
                }
                
                return context;
              }
              catch ( Exception e) {
                e.printStackTrace();
                return null;
              }
            }
          }
          );
      if (clientContext != null) {
        System.out.println("KerberizedServer: Client authenticated: (principal: " + clientContext.getSrcName() + ")");
        // ..conduct business with client since it's authenticated and optionally encrypted too..
      }
      
      System.out.println("KerberizedServer: Closing connection with client "
                         + socket.getInetAddress());
      socket.close();
    }
  }
}
