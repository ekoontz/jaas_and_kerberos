import java.io.FileInputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.net.Socket;
import java.util.Properties;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;
import javax.security.sasl.SaslException;

import javax.security.sasl.Sasl;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.SaslClient;
import java.security.PrivilegedExceptionAction;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;

import java.io.DataInputStream;
import java.io.DataOutputStream;

public class SASLizedChatClient {
  
  public static void main(String[] args) throws SaslException {
    
    // Lots of diagnostics.
    //    System.setProperty("sun.security.krb5.debug", "true");
    System.setProperty("javax.security.sasl.level","FINEST");
    System.setProperty("handlers", "java.util.logging.ConsoleHandler");
    System.setProperty("java.util.logging.ConsoleHandler","FINEST");

    // <Constants>
    final String JAAS_CONF_FILE_NAME = "jaas.conf";

    final String HOST_NAME = "ekoontz"; // The hostname that the client (this code) is running on. (might be fully qualified, or not)

    final String CLIENT_PRINCIPAL_NAME = "testclient"; // The client principal.
    final String SERVICE_PRINCIPAL_NAME = "testserver"; // The service principal.

    final String CLIENT_SECTION_OF_JAAS_CONF_FILE = "Client"; // The section (of the JAAS configuration file named $JAAS_CONF_FILE_NAME)
                                                              // that will be used to configure relevant parameters to do Kerberos authentication.
    // </Constants>

    System.setProperty( "java.security.auth.login.config", JAAS_CONF_FILE_NAME);

    // 1. Login to Kerberos.
    Properties props = new Properties();
    try {
      props.load( new FileInputStream(args[0]));
    }
    catch (IOException e) {
      System.err.println("Client: Error opening properties file '"+args[0]+"': " + e);
      e.printStackTrace();
      System.exit(-1);
    }

    Subject subject = null;
    try {
      // Login to the KDC.
      LoginContext loginCtx = null;
      String password = props.getProperty( "client.password");
      loginCtx = new LoginContext(CLIENT_SECTION_OF_JAAS_CONF_FILE,
                                  new LoginCallbackHandler( CLIENT_PRINCIPAL_NAME, password));

      loginCtx.login();
      subject = loginCtx.getSubject();
    }
    catch (LoginException e) {
      System.err.println("Kerberos login failure : " + e);
      System.exit(-1);
    }

    // 2. Create SASL client.
    SaslClient sc = null;
    try {
      sc = Subject.doAs(subject,new PrivilegedExceptionAction<SaslClient>() {
          public SaslClient run() throws SaslException {
            
            System.out.println("CREATING SASL CLIENT OBJECT NOW...");
            String[] mechs = {"GSSAPI"};
            SaslClient saslClient = Sasl.createSaslClient(mechs,
                                                          CLIENT_PRINCIPAL_NAME,
                                                          SERVICE_PRINCIPAL_NAME,
                                                          HOST_NAME,
                                                          null,
                                                          new ClientCallbackHandler());
            
            System.out.println("DONE CREATING SASL CLIENT OBJECT.");
            return saslClient;
          }
        });
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    if (sc != null) {
      System.out.println("SaslClient object successfully created.");
    }

    // 3. Connect to service.
    String hostName = args[1];
    int port = Integer.parseInt(args[2]);
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


      // 3.5 Tell server we want to do SASL authentication.
      String authCommand = "/auth\n";
      outStream.writeBytes(authCommand);
      outStream.flush();

      // 4. Establish SASL connection with server.
      final SaslClient saslClient = sc;
      Object result;
      System.out.println("ESTABLISHING SASL CONNECTION WITH THE '" + SERVICE_PRINCIPAL_NAME + "' service.");
      try {
        result =
          Subject.doAs(subject,new PrivilegedExceptionAction<Object>() {
              public Object run() {
                try {
                  byte[] saslToken = new byte[0];

                  if (saslClient.hasInitialResponse()) {
                    System.out.println("evaluating initial response:");
                    saslToken = saslClient.evaluateChallenge(saslToken);
                    System.out.println("evaluateChallenge(): response has length : " + saslToken.length);
                    Hexdump.hexdump(System.out,saslToken,0,saslToken.length);
                  }

                  byte[] emptyArray = new byte[0];

                  while (!saslClient.isComplete()) {
                    System.out.println("");
                    System.out.println("<client challenge/response loop>");
                    System.out.println("writing response of length: " + saslToken.length);
                    // <corrupt bytes to test server response..>
                    /*                    saslToken[5] = (byte)0;
                    saslToken[6] = (byte)1;
                    saslToken[7] = (byte)2;
                    saslToken[8] = (byte)3;*/
                    // </corrupt bytes to test server response..>
                    //                    outStream.writeInt(saslToken.length);
                    //                    outStream.flush();

                    if (saslToken.length == 0) {
                      String nomsg = new String("(C:nomsg)");
                      outStream.write(nomsg.getBytes(),0,nomsg.getBytes().length);
                    }
                    else {
                      outStream.write(saslToken, 0, saslToken.length);
                    }
                    outStream.flush();
                    System.out.println("wrote response.");

                    int length;
                    saslToken = new byte[8192];
                    System.out.println("reading challenge...");
                    length = inStream.read(saslToken);

                    String testMessage = new String(saslToken);
                    if (testMessage.trim().equals("(S:nomsg)")) {
                      System.out.println("Just read a 'nomsg' response from server.");
                      length = 0;
                      saslToken = new byte[0];
                    }

                    if (length > 0) {
                      System.out.println("**read challenge: saslToken length: " + saslToken.length);
                      Hexdump.hexdump(System.out,saslToken,0,saslToken.length);
                    }
                    else {
                      System.out.println("zero-length challenge: nothing to read from server.");
                    }

                    saslToken = saslClient.evaluateChallenge(saslToken);
                    System.out.println("evaluateChallenge(): response has length : " + saslToken.length);

                    if (saslClient.isComplete()) {
                      System.out.println("CLIENT SAYS: COMPLETE.");
                    }
                    else {
                      System.out.println("CLIENT SAYS: NOT COMPLETE.");
                    }

                    System.out.println("</client challenge/response loop>");
                  }
                  System.out.println("Done with authentication loop. Sending last token.");
                  outStream.write(saslToken, 0, saslToken.length);
                  outStream.flush();

                  System.out.println("SASL client context established. Negotiated QoP: "
                                     + saslClient.getNegotiatedProperty(Sasl.QOP));
                  return true;
                } catch (IOException e) {
                  System.err.println("IOException happened in run():");
                  e.printStackTrace();
                }
                return null;
              }
            });

      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
    catch (IOException e) {
      e.printStackTrace();
      System.err.println("Client: There was an error in creating a name for the host-based service that we want to connect to.");
      System.exit(-1);
    }

  }

  public enum SaslStatus {
    SUCCESS (0),
    ERROR (1);

    public final int state;
    private SaslStatus(int state) {
      this.state = state;
    }
  }

  private static void readStatus(DataInputStream inStream) throws IOException {
    int status = inStream.readInt(); // read status
    if (status != SaslStatus.SUCCESS.state) {
      throw new IOException("SaslStatus was not SUCCESS.");
    }
  }

  private static class ClientCallbackHandler implements CallbackHandler {
    @Override
    public void handle(Callback[] callbacks) throws
        UnsupportedCallbackException {
      System.out.println("ClientCallbackHandler::handle()");
      AuthorizeCallback ac = null;
      for (Callback callback : callbacks) {
        System.out.println("ClientCallbackHandler::handle()::each callback");        
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


}