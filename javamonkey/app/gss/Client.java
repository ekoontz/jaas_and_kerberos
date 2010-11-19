package javamonkey.app.gss;
 
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;
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
import java.io.DataInputStream;
import java.io.DataOutputStream;
 
public class Client {

  static Oid krb5Oid;
 
  public static void main( String[] args) {
    try {
      // Oid mechanism = use Kerberos V5 as the security mechanism.
      krb5Oid = new Oid( "1.2.840.113554.1.2.2");

      // Setup up the Kerberos properties.
      Properties props = new Properties();
      props.load( new FileInputStream( "client.properties"));
      System.setProperty( "sun.security.krb5.debug", "true");
      System.setProperty( "java.security.auth.login.config", "./jaas.conf");
      System.setProperty( "javax.security.auth.useSubjectCredsOnly", "true");
      String username = props.getProperty( "client.principal.name");
      String password = props.getProperty( "client.password");

      // 1. Initialize client object.
      Client client_net = new Client();

      // 2. Authenticate against the KDC using JAAS and return the Subject.
      LoginContext loginCtx = null;
      // "Client" references the corresponding JAAS configuration section in the jaas.conf file.
      loginCtx = new LoginContext( "Client",
                                   new LoginCallbackHandler( username, password));
      loginCtx.login();
      Subject subject = loginCtx.getSubject();

      // 3. Connect to service and authenticate.
      String server = args[0];
      String hostName = args[1];
      int port = Integer.parseInt(args[2]);
      Socket socket = new Socket(hostName,port);
      final DataInputStream inStream   = new DataInputStream(socket.getInputStream());
      final DataOutputStream outStream = new DataOutputStream(socket.getOutputStream());
      String servicePrincipalName = props.getProperty( "service.principal.name");

      GSSManager manager = GSSManager.getInstance();
      GSSName serverName = manager.createName( servicePrincipalName, GSSName.NT_HOSTBASED_SERVICE);

      System.out.println("Client.initiateSecurityContextNet() Initiate security context with serverName " + serverName);

      final GSSContext context = manager.createContext( serverName, 
                                                        krb5Oid, 
                                                        null,
                                                        GSSContext.DEFAULT_LIFETIME);
      
      context.requestMutualAuth(true);  // Mutual authentication
      
      // The GSS context initiation has to be performed as a privileged action.
      byte[] serviceTicket = Subject.doAs( subject, new PrivilegedAction<byte[]>() {
          public byte[] run() {
            try {
              byte[] token = new byte[0];
              context.requestMutualAuth( false);
              context.requestCredDeleg( false);
              
              int retval;
              while(!context.isEstablished()) {
                retval = context.initSecContext(inStream,outStream);
              }
              return token;
            }
            catch ( GSSException e) {
              e.printStackTrace();
              return null;
            }
          }
        });

      System.out.println("Authenticated with service successfully.");


    }
    catch ( LoginException e) {
      e.printStackTrace();
      System.err.println( "There was an error during the JAAS login");
      System.exit( -1);
    }
    catch ( GSSException e) {
      e.printStackTrace();
      System.err.println( "There was an error during the security context initiation");
      System.exit( -1);
    }
    catch ( IOException e) {
      e.printStackTrace();
      System.err.println( "There was an IO error");
      System.exit( -1);
    }
  }

}
