import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import java.security.PrivilegedExceptionAction;
import java.security.PrivilegedActionException;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

public class NIOServerSASL extends NIOServerMultiThread {

  Subject chatServerSubject;

  // <Constants>
  final String JAAS_CONF_FILE_NAME = "jaas.conf";
  
  final String HOST_NAME = "ekoontz"; // The hostname that the service (this code) is running on. (might be fully qualified, or not)
  
  final String SERVICE_PRINCIPAL_NAME = "testserver"; // The service that we're running with this code.
  // There must exist a Kerberos principal called
  // $SERVICE_PRINCIPAL_NAME/$HOSTNAME.
  
  final String SERVICE_SECTION_OF_JAAS_CONF_FILE = "Server"; // The section (of the JAAS configuration file named $JAAS_CONF_FILE_NAME)
  
  final String KEY_TAB_FILE_NAME = "testserver.keytab"; // The file that holds the service's credentials.
  // </Constants>

  public enum ClientState {
    Unauthenticated,Authenticating,Authenticated
  };

  // client state table
  public ConcurrentHashMap<SelectionKey,ClientState> clientStates;

  @Override 
    protected synchronized void ReadFromClient(SelectionKey sk) {
      if (clientStates.get(sk) == null) {
        clientStates.put(sk,ClientState.Unauthenticated);
      }
      super.ReadFromClient(sk);
    }

  public void initializeClientStates() {
    // Should be called only once, at server startup time.

    clientStates = new ConcurrentHashMap<SelectionKey,ClientState>();
  }

  public void authenticateServer() {
    // Should be called only once, at server startup time.
    
    //    System.setProperty("sun.security.krb5.debug", "true");
    System.setProperty("javax.security.sasl.level","FINEST");
    System.setProperty("handlers", "java.util.logging.ConsoleHandler");
    
  
    System.setProperty( "java.security.auth.login.config", JAAS_CONF_FILE_NAME);

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

    try {
      
      // 1. Login to Kerberos.
      LoginContext loginCtx = null;
      System.out.println("Authenticating using '" + SERVICE_SECTION_OF_JAAS_CONF_FILE + "' section of '" + JAAS_CONF_FILE_NAME + "'...");
      loginCtx = new LoginContext(SERVICE_SECTION_OF_JAAS_CONF_FILE);
      loginCtx.login();
      chatServerSubject = loginCtx.getSubject();

      System.out.println("..authenticated successfully.");
    }
    catch (LoginException e) {
      System.err.println("LoginException: : " + e);
      e.printStackTrace();
      System.exit(-1);
    }


    
  }
 
  public static void main(String[] args) 
    throws IOException {
    // Obtain the command-line arguments and parse the port number
    
    if (args.length != 1) {
      System.err.println("Usage: java <options> NIOServerSASL <localPort>");
      System.exit(-1);
    }
    
    NIOServerSASL instance = new NIOServerSASL();

    int localPort = Integer.parseInt(args[0]);
    instance.StartThreadsAndRun(localPort);
    
  }

  @Override public void StartThreadsAndRun(int port) 
    throws IOException {

    try {
      authenticateServer();

      initializeClientStates();

      setupQueues();

      AuthReadWorker reader = new AuthReadWorker(this);
      AuthWriteWorker writer = new AuthWriteWorker(this);

      new Thread(reader).start();
      new Thread(writer).start();
      
      // main thread.
      run(port);
    }
    catch (IOException e) {
      throw e;
    }

  }

  protected boolean ProcessClientMessage(SelectionKey sk,String clientMessage) {
    boolean result = super.ProcessClientMessage(sk,clientMessage);
    if (result == false) {
      // try SASL-specific processing.
      if (clientMessage.substring(0,5).equals("/auth")) {
        clientStates.put(sk,ClientState.Authenticating);
        return true;
      }
      else {
        if (clientMessage.substring(0,7).equals("/status")) {

          // FIXME: (only authenticated clients can use this).

          // Construct a human-readable table of clients and their states
          // and send to client.
          String stateTable = "";
          stateTable = stateTable + "===Client->State===" + "\n";
          for (SelectionKey client: clientNick.keySet()) {
            String nick = clientNick.get(client);
            stateTable = stateTable + "\t" + nick;
            stateTable = stateTable + "\t" + clientStates.get(client);
            if (client == sk) {
              stateTable = stateTable + " <= you";
            }
            stateTable = stateTable + "\n";
          }
          Send(stateTable,sk);
          return true;
        }
        else {
          Send("unrecognized command: " + clientMessage,sk);
        }
      }
    }
    return false;
  }

  public String ShowCommands() {
    String retval = super.ShowCommands();
    retval += "/auth - authenticate using SASL.\n";
    retval += "/status - show authentication status for all clients.\n";
    return retval;
  }

  protected boolean DoAuthentication(SelectionKey sk) {

    SaslServer saslServer = CreateSaslServer(chatServerSubject, "GSSAPI",SERVICE_PRINCIPAL_NAME,HOST_NAME);

    if (saslServer == null) {
      return false;
    }
    while (!saslServer.isComplete()) {
      try {
        exchangeTokens(saslServer,sk);
        if (true) {
          // break out early for now.
          break;
        }
      }
      catch (SaslException e) {
        System.err.println("authentication with client failed: closing client connection.");
        CancelClient(sk);
        return false;
      }
    }

    return true;
  }

  public static SaslServer CreateSaslServer(final Subject subject, final String mech,final String principalName,final String hostName) {
    try {
      return Subject.doAs(subject,new PrivilegedExceptionAction<SaslServer>() {
          public SaslServer run() {
            try {
              SaslServer saslServer;
              System.out.println("creating SaslServer with service subject..");
              saslServer = Sasl.createSaslServer(mech,principalName,hostName,null,new ServerCallbackHandler());
              System.out.println("..done.");
              return saslServer;
            }
            catch (SaslException e) {
              System.err.println("Error creating SaslServer.");
              e.printStackTrace();
              return null;
            }
          }
        }
        );
    }
    catch (PrivilegedActionException e) {
      System.err.println("Error creating SaslServer object while calling doAs((principal='" + principalName + "'),..)");
      e.printStackTrace();
    }
    return null;
  }

  private void exchangeTokens(SaslServer saslServer, SelectionKey sk) throws SaslException {

    // move client to token-exchange state.
    String test = "foobar";

    System.out.println("Server:exchangeTokens(): read string: "+test);

    return;

    /*      try {
      int length = ReadIntFromClientByNetwork(sk);
      System.out.println("Server:exchangeTokens(): read integer value: " + length);
      

      byte[] saslToken = ReadBytesFromClientByNetwork(sk);
      System.out.println("Server:exchangeTokens(): response token read of length " + saslToken.length);

      saslToken = saslServer.evaluateResponse(saslToken);
      if (saslToken != null) {
        if (saslToken.length > 0) {
          WriteBytesToClientByNetwork(sk,saslToken);
          System.out.println("Wrote token of length: " + saslToken.length);
        }
        else {
          //              outStream.writeInt(0);
          System.out.println("Challenge length is 0: not sending (just sending integer 0 length).");
        }
      }
      else {
        System.out.println("evaluateResponse() returned a null token: continuing without writing anything to client.");
      }
    }
    catch (IOException e) {
      System.err.println("Failed to exchange token with client.");
      e.printStackTrace();
      CancelClient(sk);
      }*/
    }
}

class ServerCallbackHandler implements CallbackHandler {
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

/*
class AuthWorker implements Runnable {
    public AuthWorker(NIOServerSASL mainObject) {
      main = mainObject;
    }
    
    public void run() {
      System.out.println("starting AuthWorker..");

      while(true) {
        // blocks waiting for items to appear on the read queue.
        System.out.println("AuthWorker.run(): waiting for a client to appear in the auth queue.");
        SelectionKey readFromMe = main.takeFromAuthQueue();
        String message = ReadFromClientByNetwork(readFromMe);
                
        if (message == null) {
          System.out.println("AuthWorker: message is null: assuming client hung up.");
          CancelClient(readFromMe);
        }
        else {
          if (message.trim().length() == 0) {
            // FIXME: happens after we've just read a message
            // from readFromMe, but still readFromMe is added to queue.
          }
          else {
            System.out.println("AuthWorker:run(): read key : " + readFromMe + " and got message: [" + message + "].");
            ProcessClientMessage(readFromMe,message);
          }
        }
      }
    }
  }


        boolean auth_result = DoAuthentication(sk);
        if (auth_result == true) {
          Send("You are now authenticated.",sk);
        }
        else {
          Send("You are NOT authenticated.",sk);
        }

*/