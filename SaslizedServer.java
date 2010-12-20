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

import java.io.DataInputStream;
import java.io.DataOutputStream;

public class SASLizedServer {
  
  public static void main(String[] args) throws SaslException {
    new SASLizedServer().start();
  }
  
  private void start() throws SaslException {
    
    byte[] challenge;
    byte[] response;
    
    // Lots of diagnostics.
    //    System.setProperty("sun.security.krb5.debug", "true");
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

    final Subject subject;
    try {
      // Login to the KDC.
      LoginContext loginCtx = null;
      loginCtx = new LoginContext(SERVICE_SECTION_OF_JAAS_CONF_FILE);
      loginCtx.login();
      subject = loginCtx.getSubject();


      ServerSocket serverListenSocket = null;
      try {
        serverListenSocket = new ServerSocket(SERVER_PORT);
      }
      catch (IOException e) {
        System.err.println("new ServerSocket() failure : " + e);
        System.exit(-1);
        e.printStackTrace();
      }

      int clientConnectionNumber = 0;

      while(true) {
        System.out.println("WAITING FOR CONNECTIONS...");
        

        Socket clientConnectionSocket = null;
        
        try {
          clientConnectionSocket = serverListenSocket.accept();
        }
        catch (IOException e) {
          System.err.println("sock.accept() failure : " + e);
          System.exit(-1);
          e.printStackTrace();
          if (clientConnectionSocket != null) {
            try {
              clientConnectionSocket.close();
            }
            catch (Exception ex) {
              System.out.println("Error closing clientConnectionSocket().");
            }
          }
        }
        
        try {
          final DataInputStream inStream = new DataInputStream(clientConnectionSocket.getInputStream());
          final DataOutputStream outStream = new DataOutputStream(clientConnectionSocket.getOutputStream());
          
          System.out.println("CONNECTED.");
          
          System.out.println("DOING SASL AUTHENTICATION.");
          
          try {
            SaslServer saslServer =
              Subject.doAs(subject,new PrivilegedExceptionAction<SaslServer>() {
                  public SaslServer run() {
                    System.out.println("run() starting..");
                    SaslServer saslServer = null;

                    try {
                      saslServer = Sasl.createSaslServer("GSSAPI",
                                                         SERVICE_PRINCIPAL_NAME,
                                                         HOST_NAME,
                                                         null,
                                                         new ServerCallbackHandler());
                      
                      System.out.println("DONE CREATING SERVER.");
                      
                      // Perform authentication steps until done
                      while (!saslServer.isComplete()) {
                        System.out.println("");
                        System.out.println("");
                        try {
                          int length = inStream.readInt();
                          System.out.println("Server: read integer: " + length);
                          byte[] saslToken = new byte[length];
                          inStream.readFully(saslToken,0,length);
                          System.out.println("Server: response token read of length " + saslToken.length);
                          byte[] challengeToken = new byte[4096];
                          try {
                            challengeToken = saslServer.evaluateResponse(saslToken);
                          }
                          catch (SaslException e) {
                            System.err.println("Oops: attempt to evaluate response caused a SaslException: closing connection with this client.");
                            e.printStackTrace();
                            return null;
                          }
                          
                          if (challengeToken != null) {
                            if (challengeToken.length > 0) {
                              outStream.writeInt(challengeToken.length);
                              outStream.write(challengeToken,0,challengeToken.length);
                              outStream.flush();
                              System.out.println("Wrote token of length: " + challengeToken.length);
                            }
                            else {
                              outStream.writeInt(0);
                              System.out.println("Challenge length is 0: not sending (just sending integer 0 length).");
                            }
                          }
                        }
                        catch (IOException e) {
                          System.err.println("Failed to read integer from client.");
                          e.printStackTrace();
                          return null;
                        }
                      }
                      System.out.println("Finished authenticated client: authorization id: " + saslServer.getAuthorizationID());
                      return saslServer;
                    }
                    catch (SaslException e) {
                      System.err.println("Error authenticating client.");
                      e.printStackTrace();
                    }
                    return saslServer;
                  }
                });
                
                System.out.println("Writing actual message payload after authentication.");
                outStream.writeInt(clientConnectionNumber);
                clientNumber++;
          }
          catch (Exception e) {
            System.err.println("Caught exception:");
            e.printStackTrace();
          }
          System.out.println("Closing client connection.");
          //        saslServer.dispose();
          clientConnectionSocket.close();
        }
        catch (Exception ioe) {
          System.err.println("Caught exception:");
          ioe.printStackTrace();
        }
      }      
    }
    catch (LoginException e) {
      System.err.println("Kerberos login failure : " + e);
      System.exit(-1);
    }
    
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
          if (true) {
            System.out.println("authid != authzid; setting to authorized anyway.");
            ac.setAuthorized(true);
          }
          else {
            ac.setAuthorized(false);
          }
        }
        if (ac.isAuthorized()) {
          ac.setAuthorizedID(authzid);
        }
      }
    }
  }

}
