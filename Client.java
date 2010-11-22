import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.PrivilegedAction;
import java.util.Properties;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
 
public class Client {

  static Oid krb5Oid;
 
  public static void main( String[] args) {

    // Oid mechanism = use Kerberos V5 as the security mechanism.
    try {
      krb5Oid = new Oid( "1.2.840.113554.1.2.2");
    }
    catch (GSSException e) {
      System.err.println("Client: Error obtaining Kerberos V5 OID: " + e);
      e.printStackTrace();
      System.exit(-1);
    }

    // 1. Set up Kerberos properties.
    Properties props = new Properties();
    try {
      props.load( new FileInputStream( "client.properties"));
    }
    catch (IOException e) {
      System.err.println("Client: Error opening properties file 'client.properties': " + e);
      e.printStackTrace();
      System.exit(-1);
    }

    System.setProperty( "sun.security.krb5.debug", "true");
    System.setProperty( "java.security.auth.login.config", "./jaas.conf");
    System.setProperty( "javax.security.auth.useSubjectCredsOnly", "true");

    // 2. Authenticate against the KDC using JAAS and return the Subject.
    String username = props.getProperty( "client.principal.name");
    String password = props.getProperty( "client.password");
    LoginContext loginCtx = null;
    // "Client" references the corresponding JAAS configuration section in the jaas.conf file.
    try {
      loginCtx = new LoginContext("Client",
                                  new LoginCallbackHandler( username, password));
      loginCtx.login();
    }
    catch ( LoginException e) {
      System.err.println("Client: There was an error during the JAAS login: " + e);
      e.printStackTrace();
      System.exit( -1);
    }

    Subject subject = loginCtx.getSubject();

    // 3. Connect to service.
    String hostName = args[0];
    int port = Integer.parseInt(args[1]);
    Socket socket = null;
    try {
      socket = new Socket(hostName,port);
    }
    catch (UnknownHostException e) {
        e.printStackTrace();
        System.err.println("Client: There was an error connecting to the server: hostname " + hostName + " not found.");
        System.exit( -1);
    }
    catch (IOException e) {
        e.printStackTrace();
        System.err.println("Client: There was an error connecting to the server: " + e);
        System.exit( -1);
    }

    final DataInputStream inStream;
    final DataOutputStream outStream;

    try {
      inStream = new DataInputStream(socket.getInputStream());
      outStream = new DataOutputStream(socket.getOutputStream());

      // 4. Authenticate with service.
      String servicePrincipalName = props.getProperty("service.principal.name");
      GSSManager manager = GSSManager.getInstance();
      GSSName serverName = null;

      try { 
        serverName = manager.createName( servicePrincipalName, GSSName.NT_HOSTBASED_SERVICE);
      }
      catch (GSSException e) {
        e.printStackTrace();
        System.err.println("Client: There was an error in creating a name for the host-based service that we want to connect to.");
        System.exit(-1);
      }
      
      System.out.println("Client: Initiating security context with serverName " + serverName);
      
      try {
        final GSSContext context = manager.createContext( serverName, 
                                                          krb5Oid, 
                                                          null,
                                                          GSSContext.DEFAULT_LIFETIME);

        // The GSS context initiation has to be performed as a privileged action.
        GSSContext serviceTicket = Subject.doAs( subject, new PrivilegedAction<GSSContext>() {
            public GSSContext run() {
              try {
                context.requestMutualAuth( false);
                context.requestCredDeleg( false);
                
                int retval;
                while(!context.isEstablished()) {
                  context.initSecContext(inStream,outStream);
                }
                return context;
              }
              catch (GSSException e) {
                e.printStackTrace();
                return null;
              }
            }
          });

        if (serviceTicket != null) {
          System.out.println("Client obtained service ticket for service : " + servicePrincipalName);
          if (true) {
          try {
            Thread.currentThread().sleep(5000);
            socket.close();
          }
          catch (InterruptedException e) {
            //...
          }
          }
        }
        else {
          System.out.println("Client failed to obtain service ticket for service : " + servicePrincipalName);
          System.exit(-1);
        }
      }
      catch (GSSException e) {
        e.printStackTrace();
        System.exit(-1);
      }
    }
    catch ( IOException e) {
      e.printStackTrace();
      System.err.println( "Client: There was an IO error");
      System.exit( -1);
    }
  }

}
