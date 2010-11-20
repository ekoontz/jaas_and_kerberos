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

public class KerberizedServer  {
  // Oid mechanism = use Kerberos V5 as the security mechanism.
  static Oid krb5Oid;
    
    public static void main(String[] args) 
	throws IOException, GSSException {
      // 1. Set up Kerberos properties.
      // 1.1. Oid mechanism = use Kerberos V5 as the security mechanism.
      krb5Oid = new Oid( "1.2.840.113554.1.2.2");

      // 1.2 Properties
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
        System.err.println("Usage: java <options> SampleServer <localPort>");
        System.exit(-1);
      }
      
      // 3. Service clients.
      // 3.1. Startup service network connection.
      int localPort = Integer.parseInt(args[0]);
      ServerSocket ss = new ServerSocket(localPort);
      
      while (true) {
        
        System.out.println("SampleServer::main() Waiting for client connection...");
        

        // 3.2 Receive a client connection.
        Socket socket = ss.accept();
        final DataInputStream inStream =
          new DataInputStream(socket.getInputStream());
        final DataOutputStream outStream = 
          new DataOutputStream(socket.getOutputStream());

        System.out.println("SampleServer::main() Got connection from client "
                           + socket.getInetAddress());
	
        Subject.doAs( subject, new PrivilegedAction<String>() {
            public String run() {
              try {
                // Authenticate the client; return principal name

                GSSManager manager = GSSManager.getInstance();
                GSSContext context = manager.createContext( (GSSCredential) null);
                
                while (!context.isEstablished()) {
                  System.out.println("SampleServer::main() context not yet established: accepting from client.");
                  
                  context.acceptSecContext(inStream,outStream);
                }
                
                System.out.println("Client authenticated: (principal: " + context.getSrcName() + ")");
                return context.getSrcName().toString();
              }
              catch ( Exception e) {
                e.printStackTrace();
                return null;
              }
            }
          }
          );

        // ..conduct business with client since it's authenticated and optionally encrypted too.
	
        System.out.println("SampleServer::main() Closing connection with client " 
                           + socket.getInetAddress());
        socket.close();
      }
    }
}
