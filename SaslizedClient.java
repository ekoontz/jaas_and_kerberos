import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.sasl.SaslException;

public class SaslizedClient {
  
  public static void main(String[] args) throws SaslException {

    byte[] challenge;
    byte[] response;
    
    // Lots of diagnostics.
    System.setProperty("sun.security.krb5.debug", "true");
    System.setProperty("javax.security.sasl.level","FINEST");
    System.setProperty("handlers", "java.util.logging.ConsoleHandler");

    // <Constants>
    final String JAAS_CONF_FILE_NAME = "jaas.conf";

    final String HOST_NAME = "ekoontz"; // The hostname that the client (this code) is running on. (might be fully qualified, or not)

    final String CLIENT_PRINCIPAL_NAME = "testclient"; // The service that's running (must exist as a Kerberos principal $CLIENT_PRINCIPCAL_NAME).

    final String CLIENT_SECTION_OF_JAAS_CONF_FILE = "Client"; // The section (of the JAAS configuration file named $JAAS_CONF_FILE_NAME)
    //    final String CLIENT_SECTION_OF_JAAS_CONF_FILE = "SaslizedClient"; // The section (of the JAAS configuration file named $JAAS_CONF_FILE_NAME)
                                                                      // that will be used to configure relevant parameters to do Kerberos authentication.


    final Integer SERVER_PORT = 4567; // The port on which SaslizedServer is listening.
    // </Constants>

    System.setProperty( "java.security.auth.login.config", JAAS_CONF_FILE_NAME);

    Subject subject = null;
    try {
      // Login to the KDC.
      LoginContext loginCtx = null;
      String username = props.getProperty( "client.principal.name");
      String password = props.getProperty( "client.password");
      loginCtx = new LoginContext(CLIENT_SECTION_OF_JAAS_CONF_FILE,
                                  new LoginCallbackHandler( username, password));

      loginCtx.login();
      subject = loginCtx.getSubject();
    }
    catch (LoginException e) {
      System.err.println("Kerberos login failure : " + e);
      System.exit(-1);
    }



  }
}