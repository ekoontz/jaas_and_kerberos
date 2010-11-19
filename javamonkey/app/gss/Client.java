package javamonkey.app.gss;
 
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
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
import sun.misc.BASE64Encoder;
import java.io.DataInputStream;
import java.io.DataOutputStream;
 
/**
 * <p>Client logs in to a Key Distribution Center (KDC) using JAAS and then
 * requests a service ticket for the server, base 64 encodes it and writes it
 * to the file <i>security.token</i>.</p>
 * <p>This class, in combination with the <i>Server</i> class illustrates the
 * use of the JAAS and GSS APIs for initiating a security context using the
 * Kerberos protocol.</p>
 * <p>This requires a KDC/domain controller such as Active Directory or Apache
 * Directory. The KDC configuration details are stored in the
 * <i>client.properties</i> file, while the JAAS details are stored in the
 * file <i>jaas.conf</i>.</p>
 * @author Ants
 */
public class Client {
 
  public static void main( String[] args) {
    try {
      // Setup up the Kerberos properties.
      Properties props = new Properties();
      props.load( new FileInputStream( "client.properties"));
      System.setProperty( "sun.security.krb5.debug", "true");
      System.setProperty( "java.security.auth.login.config", "./jaas.conf");
      System.setProperty( "javax.security.auth.useSubjectCredsOnly", "true");
      String username = props.getProperty( "client.principal.name");
      String password = props.getProperty( "client.password");
      // Oid mechanism = use Kerberos V5 as the security mechanism.
      krb5Oid = new Oid( "1.2.840.113554.1.2.2");


      System.out.println("===begin client auth(1)===");
      // 1. Initialize client object.
      Client client_fs = new Client();

      // 2. Login to the KDC.
      client_fs.login(username, password);

      // 3. Request the service ticket.
      client_fs.initiateSecurityContext(props.getProperty( "service.principal.name"));

      // 4. Write to file.
      encodeAndWriteTicketToDisk( client_fs.serviceTicket, "./security.token");
      System.out.println( "Service ticket encoded to disk successfully(1)");
      System.out.println("===end client auth(1)===");
      System.out.println("");


      System.out.println("===begin client auth(2)===");
      // 1. Initialize client object.
      Client client_net = new Client();

      // 2. Login to the KDC.
      client_net.login( username, password);

      // 3. Request the service ticket.
      String server = args[0];
      String hostName = args[1];
      int port = Integer.parseInt(args[2]);
      Socket socket = new Socket(hostName,port);
      DataInputStream inStream   = new DataInputStream(socket.getInputStream());
      DataOutputStream outStream = new DataOutputStream(socket.getOutputStream());
      client_net.initiateSecurityContextNet(props.getProperty( "service.principal.name"), inStream,outStream);
      System.out.println("===end client auth(2)===");

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
 
  private static Oid krb5Oid;
 
  private Subject subject;
  private byte[] serviceTicket;
 
  // Authenticate against the KDC using JAAS.
  private void login( String username, String password) throws LoginException {
    LoginContext loginCtx = null;
    // "Client" references the JAAS configuration in the jaas.conf file.
    loginCtx = new LoginContext( "Client",
        new LoginCallbackHandler( username, password));
    loginCtx.login();
    this.subject = loginCtx.getSubject();
  }
 
  // Begin the initiation of a security context with the target service.
  private void initiateSecurityContext(String servicePrincipalName)
      throws GSSException {
    System.out.println("initiateSecurityContext("+servicePrincipalName+")");
    GSSManager manager = GSSManager.getInstance();
    GSSName serverName = manager.createName( servicePrincipalName,
        GSSName.NT_HOSTBASED_SERVICE);

    System.out.println("Client.initiateSecurityContext() Initiate security context with serverName " + serverName);

    final GSSContext context = manager.createContext( serverName, 
                                                      krb5Oid, 
                                                      null,
                                                      GSSContext.DEFAULT_LIFETIME);

    context.requestMutualAuth(true);  // Mutual authentication
    context.requestConf(true);  // Will use confidentiality later
    context.requestInteg(true); // Will use integrity later

    // The GSS context initiation has to be performed as a privileged action.
    this.serviceTicket = Subject.doAs( this.subject, new PrivilegedAction<byte[]>() {
      public byte[] run() {
        try {
          byte[] token = new byte[0];
          // This is a one pass context initialisation.
          context.requestMutualAuth( false);
          context.requestCredDeleg( false);
          byte[] retval;
          retval = context.initSecContext( token, 0, token.length);
          return retval;
        }
        catch ( GSSException e) {
          e.printStackTrace();
          return null;
        }
      }
    });

    return;
  }

  private void initiateSecurityContextNet(String servicePrincipalName, DataInputStream inStream, DataOutputStream outStream)
      throws GSSException {
    System.out.println("initiateSecurityContextNet("+servicePrincipalName+")");
    GSSManager manager = GSSManager.getInstance();
    GSSName serverName = manager.createName( servicePrincipalName,
        GSSName.NT_HOSTBASED_SERVICE);

    System.out.println("Client.initiateSecurityContext() Initiate security context with serverName " + serverName);

    final GSSContext context = manager.createContext( serverName, 
                                                      krb5Oid, 
                                                      null,
                                                      GSSContext.DEFAULT_LIFETIME);

    context.requestMutualAuth(true);  // Mutual authentication
    context.requestConf(true);  // Will use confidentiality later
    context.requestInteg(true); // Will use integrity later

    // The GSS context initiation has to be performed as a privileged action.
    this.serviceTicket = Subject.doAs( this.subject, new PrivilegedAction<byte[]>() {
      public byte[] run() {
        try {
          byte[] token = new byte[0];
          // This is a one pass context initialisation.
          context.requestMutualAuth( false);
          context.requestCredDeleg( false);
          byte[] retval;
          retval = context.initSecContext( token, 0, token.length);
          return retval;
        }
        catch ( GSSException e) {
          e.printStackTrace();
          return null;
        }
      }
    });

    return;
  }
 
  // Base64 encode the raw ticket and write it to the given file.
  private static void encodeAndWriteTicketToDisk( byte[] ticket, String filepath)
      throws IOException {
    BASE64Encoder encoder = new BASE64Encoder();    
    FileWriter writer = new FileWriter( new File( filepath));
    String encodedToken = encoder.encode( ticket);
    writer.write( encodedToken);
    writer.close();
  }


}
