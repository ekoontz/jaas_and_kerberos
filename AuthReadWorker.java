import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslException;

class AuthReadWorker extends ReadWorker {

  public AuthReadWorker(NIOServerSASL mainObject) {
    super(mainObject);
  }
  
  public void run() {
    System.out.println("starting AuthReadWorker..");
    
    while(true) {
      System.out.println("AuthReadWorker.run(): waiting for a client to appear in the read queue.");
      SelectionKey readFromMe = main.takeFromReadQueue();

      NIOServerSASL.ClientState clientState = ((NIOServerSASL)main).clientStates.get(readFromMe);
      System.out.println("AuthReadWorker client state is : " + ((NIOServerSASL)main).clientStates.get(readFromMe));

      if (clientState == NIOServerSASL.ClientState.Authenticating) {

        // 4 bytes for integer of token length.
        //        byte[] tokenLength = main.ReadBytesFromClientByNetwork(readFromMe);
        //        System.out.println("tokenLength read: " + tokenLength.length);

        byte[] saslToken = main.ReadBytesFromClientByNetwork(readFromMe);
        System.out.println("just read sasl token from client: " + saslToken.length);

        String testMessage = new String(saslToken);
        System.out.println("raw test message from client: " + testMessage);
        if (testMessage.trim().length() == 0) {
          // FIXME: happens after we've just read a message
          // from readFromMe, but still readFromMe is added to queue.
          System.out.println("AuthReadWorker: ignoring empty result from ReadBytesFromClientByNetwork()");
          continue;
        }
        
        System.out.println("AuthReadWorker: processing client authentication message of length: " + saslToken.length + ".");
        SaslServer saslServer = ((NIOServerSASL)main).saslServers.get(readFromMe);
        if (saslServer.isComplete()) {
          System.out.println("AuthReadWorker: AUTHENTICATION COMPLETE!");
          ((NIOServerSASL)main).clientStates.put(readFromMe,NIOServerSASL.ClientState.Authenticated);
        }
        else {
          System.out.println("AuthReadWorker: Auth not complete; continuing.");
          byte[] response = null;
          try {
            if (testMessage.trim().equals("(C:nomsg)")) {
              System.out.println("AuthReadWorker: evaluating response: using empty saslToken");
              saslToken = new byte[0];
            }
            response = saslServer.evaluateResponse(saslToken);

            if (response != null) {
              String sendMessage = new String(response);
              if (response.length == 0) {
                sendMessage = "(S:nomsg)";
              }
              System.out.println("sending SASL token to client: " + sendMessage);
              main.WriteToClient(readFromMe,sendMessage);
            }

          }
          catch (SaslException e) {
            System.err.println("authentication with client failed: closing client connection.");
            main.CancelClient(readFromMe);
          }
          if (saslServer.isComplete()) {
            System.out.println("COMPLETE!!! YAHOO!!!");
            ((NIOServerSASL)main).clientStates.put(readFromMe,NIOServerSASL.ClientState.Authenticated);
          }
         }
        System.out.println("AuthReadWorker: processed client authentication message; client state is : " + ((NIOServerSASL)main).clientStates.get(readFromMe));
      }
      else {
        System.out.println("AuthReadWorker: about to ReadClientMessage(): client state is : " + ((NIOServerSASL)main).clientStates.get(readFromMe));
        // non-authentication case.
        ReadClientMessage(readFromMe);
      }
    }
  }
}
