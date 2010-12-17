import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.security.PrivilegedExceptionAction;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import java.net.ServerSocket;
import java.net.Socket;

import java.io.IOException;

public class SASLizedServer {
  
  public static void main(String[] args) throws SaslException {
    new SASLizedServer().start();
  }
  
  private static class ServerCallbackHandler implements CallbackHandler {
    @Override
    public void handle(Callback[] callbacks) throws
        UnsupportedCallbackException {
      System.out.println("ServerCallbackHandler::handle()");
      AuthorizeCallback ac = null;
      for (Callback callback : callbacks) {
        if (callback instanceof AuthorizeCallback) {
          ac = (AuthorizeCallback) callback;
        } else {
          throw new UnsupportedCallbackException(callback,
              "Unrecognized SASL GSSAPI Callback");
        }
      }
      if (ac != null) {
        String authid = ac.getAuthenticationID();
        String authzid = ac.getAuthorizationID();
        if (authid.equals(authzid)) {
          ac.setAuthorized(true);
        } else {
          ac.setAuthorized(false);
        }
        if (ac.isAuthorized()) {
          ac.setAuthorizedID(authzid);
        }
      }
    }
  }
  
  private void start() throws SaslException {
    
    byte[] challenge;
    byte[] response;
    
    // Lots of diagnostics.
    System.setProperty("sun.security.krb5.debug", "true");
    System.setProperty("javax.security.sasl.level","FINEST");
    System.setProperty("handlers", "java.util.logging.ConsoleHandler");

    // <Constants>
    final String JAAS_CONF_FILE_NAME = "jaas.conf";

    final String HOST_NAME = "ekoontz"; // The hostname that the service (this code) is running on. (might be fully qualified, or not)

    final String SERVICE_PRINCIPAL_NAME = "testserver"; // The service that we're running with this code.
                                                        // There must exist a Kerberos principal called
                                                        // $SERVICE_PRINCIPAL_NAME/$HOSTNAME.

    final String SERVICE_SECTION_OF_JAAS_CONF_FILE = "Server"; // The section (of the JAAS configuration file named $JAAS_CONF_FILE_NAME)
                                                               // that will be used to configure relevant parameters to do Kerberos authentication.

    final String KEY_TAB_FILE_NAME = "testserver.keytab"; // The file that holds the service's credentials.

    final Integer SERVER_PORT = 4567; // Use anything you want here for testing - 
                                      // A real service will have a specific conventional port number. (see http://www.iana.org/assignments/port-numbers)

    // </Constants>

    //
    // The file given in JAAS_CONF_FILE_NAME must have :
    //
    // $SERVICE_SECTION_OF_JAAS_CONF_FILE {
    //   com.sun.security.auth.module.Krb5LoginModule required
    //   useKeyTab=true
    //   keyTab="$KEY_TAB_FILE_NAME"
    //   doNotPrompt=true
    //   useTicketCache=false
    //   storeKey=true
    //   debug=true
    //   principal="$SERVICE_NAME/$HOST_NAME";
    // };


    System.setProperty( "java.security.auth.login.config", JAAS_CONF_FILE_NAME);

    Subject subject = null;
    try {
      // Login to the KDC.
      LoginContext loginCtx = null;
      loginCtx = new LoginContext(SERVICE_SECTION_OF_JAAS_CONF_FILE);
      loginCtx.login();
      subject = loginCtx.getSubject();
    }
    catch (LoginException e) {
      System.err.println("Kerberos login failure : " + e);
      System.exit(-1);
    }

    if (subject == null) {
      System.err.println("Kerberos login failure: subject is null.");
      System.exit(-1);
    }

    try {
      SaslServer ss = Subject.doAs(subject,new PrivilegedExceptionAction<SaslServer>() {
          public SaslServer run() throws SaslException {
            
            System.out.println("STARTING SERVER NOW...");
            SaslServer saslServer = Sasl.createSaslServer("GSSAPI",
                                                          SERVICE_PRINCIPAL_NAME,
                                                          HOST_NAME,
                                                          null,
                                                          new ServerCallbackHandler());
            
            
            ServerSocket ss = null;
            Socket socket = null;
            
            System.out.println("DONE CREATING SERVER.");
            return saslServer;
          }
        });
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    
    System.out.println("WAITING FOR CONNECTIONS...");
    
    ServerSocket serverListenSocket = null;
    Socket clientConnectionSocket;
    try {
      serverListenSocket = new ServerSocket(SERVER_PORT);
    }
    catch (IOException e) {
      System.err.println("new ServerSocket() failure : " + e);
      System.exit(-1);
      e.printStackTrace();
    }
    
    try {
      clientConnectionSocket = serverListenSocket.accept();
    }
    catch (IOException e) {
      System.err.println("sock.accept() failure : " + e);
      System.exit(-1);
      e.printStackTrace();
    }

    System.out.println("CONNECTED.");

  }
}
