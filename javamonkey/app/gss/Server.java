package javamonkey.app.gss;
 
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.security.PrivilegedAction;
import java.util.Properties;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import sun.misc.BASE64Decoder;
 
/**
 * <p>Server logs in to a Key Distribution Center (KDC) using JAAS and then
 * reads the encoded service ticket from the file <i>service-ticket.txt</i>.
 * This ticket is decoded into a byte array and the security context is
 * attempted to be accepted, using the GSS API.</p>
 * <p>This class, in combination with the <i>Client</i> class illustrates the
 * use of the JAAS and GSS APIs for initiating a security context using the
 * Kerberos protocol.</p>
 * <p>This requires a KDC/domain controller such as Active Directory or Apache
 * Directory. The KDC configuration details are stored in the
 * <i>server.properties</i> file, while the JAAS details are stored in the
 * file <i>jaas.conf</i>.</p>
 * @author Ants
 */
public class Server {
 
  public static void main( String[] args) {
    try {
      // Setup up the Kerberos properties.
      Properties props = new Properties();
      props.load( new FileInputStream( "server.properties"));
      System.setProperty( "sun.security.krb5.debug", "true");
      System.setProperty( "java.security.krb5.realm", props.getProperty( "realm"));
      System.setProperty( "java.security.krb5.kdc", props.getProperty( "kdc"));
      System.setProperty( "java.security.auth.login.config", "./jaas.conf");
      System.setProperty( "javax.security.auth.useSubjectCredsOnly", "true");
      String password = props.getProperty( "service.password");
      // Oid mechanism = use Kerberos V5 as the security mechanism.
      krb5Oid = new Oid( "1.2.840.113554.1.2.2");
      Server server = new Server();
      // Login to the KDC.
      server.login( password);
      byte serviceTicket[] = loadTokenFromDisk();
      // Request the service ticket.
      String clientName = server.acceptSecurityContext( serviceTicket);
      System.out.println( "\nSecurity context successfully initialised!");
      System.out.println( "\nHello World " + clientName + "!");
    }
    catch ( LoginException e) {
      e.printStackTrace();
      System.err.println( "There was an error during the JAAS login");
      System.exit( -1);
    }
    catch ( GSSException e) {
      e.printStackTrace();
      System.err.println( "There was an error during the security context acceptance");
      System.exit( -1);
    }
    catch ( IOException e) {
      e.printStackTrace();
      System.err.println( "There was an IO error");
      System.exit( -1);
    }
  }
 
  // Load the security token from disk and decode it. Return the raw GSS token.
  private static byte[] loadTokenFromDisk() throws IOException {
    BufferedReader in = new BufferedReader( new FileReader( "security.token"));
    System.out.println( new File( "security.token").getAbsolutePath());
    String str;
    StringBuffer buffer = new StringBuffer();
    while ((str = in.readLine()) != null) {
       buffer.append( str + "\n");
    }
    in.close();
    //System.out.println( buffer.toString());
    BASE64Decoder decoder = new BASE64Decoder();
    return decoder.decodeBuffer( buffer.toString());
  }
 
  private static Oid krb5Oid;
 
  private Subject subject;
 
  // Authenticate against the KDC using JAAS.
  private void login( String password) throws LoginException {
    LoginContext loginCtx = null;
    // "Client" references the JAAS configuration in the jaas.conf file.
    loginCtx = new LoginContext( "Server",
        new LoginCallbackHandler( password));
    loginCtx.login();
    this.subject = loginCtx.getSubject();
  }
 
  // Completes the security context initialisation and returns the client name.
  private String acceptSecurityContext( final byte[] serviceTicket)
      throws GSSException {
    krb5Oid = new Oid( "1.2.840.113554.1.2.2");
 
    // Accept the context and return the client principal name.
    return Subject.doAs( subject, new PrivilegedAction<String>() {
      public String run() {
        try {
          // Identify the server that communications are being made to.
          GSSManager manager = GSSManager.getInstance();
          GSSContext context = manager.createContext( (GSSCredential) null);
          context.acceptSecContext( serviceTicket, 0, serviceTicket.length);
          return context.getSrcName().toString();
        }
        catch ( Exception e) {
          e.printStackTrace();
          return null;
        }
      }
    });
  }
}
