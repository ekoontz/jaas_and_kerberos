import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

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

class ClientAuthWorker implements Runnable {
  //    private Socket clientConnectionSocket;
  private Subject serverSubject;
  private String SERVICE_PRINCIPAL_NAME;
  private String HOST_NAME;
  private int clientConnectionNumber;
  
  //    ClientAuthWorker(SocketChannel s, Subject subj, String servicePrincipalName, String hostName, int clientConnectionNum) {
  ClientAuthWorker(Subject subj, String servicePrincipalName, String hostName, int clientConnectionNum) {
    
    System.out.println("ClientAuthWorker(): initializing.");
    
    //      clientConnectionSocket = s.socket();
    serverSubject = subj;
    SERVICE_PRINCIPAL_NAME = servicePrincipalName;
    HOST_NAME = hostName;
    clientConnectionNumber = clientConnectionNum;
    
    System.out.println("ClientAuthWorker(): initialized.");
  }
  
  public void run() {
    try {
      //        final DataInputStream inStream = new DataInputStream(clientConnectionSocket.getInputStream());
      //        final DataOutputStream outStream = new DataOutputStream(clientConnectionSocket.getOutputStream());
      System.out.println("Server: Connected.");
      System.out.println("Server: Doing SASL authentication.");
      
      SaslServer saslServer = createSaslServer(serverSubject, "GSSAPI",SERVICE_PRINCIPAL_NAME,HOST_NAME);
      
      // Perform authentication steps until authentication process is finished.
      while (!saslServer.isComplete()) {
        //          exchangeTokens(saslServer,inStream,outStream);
        exchangeTokens(saslServer);
      }
      
      System.out.println("Server: Successfully authenticated client with authorization id: " + saslServer.getAuthorizationID());
      System.out.println("Server: Writing actual message payload after authentication.");
      //        outStream.writeInt(clientConnectionNumber);
      System.out.println("Server: Finished writing to client.");
    }
    catch (Exception e) {
      System.err.println("ClientAuthWorker Exception: " + e);
      e.printStackTrace();
    }
    finally {
      try {
        //          clientConnectionSocket.close();
      }
      catch (Exception e) {
        System.err.println("ClientAuthWorker Exception closing client connection socket: " + e);
        e.printStackTrace();
      }
    }
  }
  
  private static SaslServer createSaslServer(final Subject subject, final String mech,final String principalName,final String hostName) {
    try {
      return Subject.doAs(subject,new PrivilegedExceptionAction<SaslServer>() {
          public SaslServer run() {
            SaslServer saslServer = null;
            try {
              System.out.println("creating SaslServer with service subject..");
              saslServer = Sasl.createSaslServer(mech,principalName,hostName,null,new ServerCallbackHandler());
              System.out.println("..done.");
            }
            catch (SaslException e) {
              System.err.println("Error creating SaslServer.");
              e.printStackTrace();
            }
            return saslServer;
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
  
  /*    private static void exchangeTokens(SaslServer saslServer, DataInputStream inStream, 
        DataOutputStream outStream) throws SaslException {*/
  private static void exchangeTokens(SaslServer saslServer) throws SaslException {
    try {
      //      int length = inStream.readInt();
      int length = 0;// TODO : nio implementation
      
      System.out.println("Server: read integer: " + length);
      byte[] saslToken = new byte[length];
      //      inStream.readFully(saslToken,0,length);
      System.out.println("Server: response token read of length " + saslToken.length);
      try {
        saslToken = saslServer.evaluateResponse(saslToken);
        if (saslToken != null) {
          if (saslToken.length > 0) {
            //              outStream.writeInt(saslToken.length);
            //              outStream.write(saslToken,0,saslToken.length);
            //              outStream.flush();
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
      catch (SaslException e) {
        System.err.println("exchangeTokens(): throwing SaslException.");
        throw e;
      }
    }
    catch (IOException e) {
      System.err.println("Failed to read integer from client.");
      e.printStackTrace();
    }
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

