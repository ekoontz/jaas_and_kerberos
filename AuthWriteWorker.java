import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

public class AuthWriteWorker extends WriteWorker {
  
  public AuthWriteWorker(NIOServerSASL mainObject) {
    super(mainObject);
  }
  
  public void run() {
    while(true) {
      System.out.println("AuthWriteWorker.run(): waiting for a client to appear in the write queue.");
      // blocks waiting for items to appear on the write queue.
      Pair<SelectionKey,String> messageTuple = main.takeFromWriteQueue();
      SelectionKey writeToMe = messageTuple.first;
      String message = messageTuple.second;

      NIOServerSASL.ClientState clientState = ((NIOServerSASL)main).clientStates.get(writeToMe);
      
      System.out.println("AuthWriteWorker.run(): state of this client: " + clientState);
      
      if (clientState == NIOServerSASL.ClientState.Authenticating) {
        byte[] messageBytes = message.getBytes();
        System.out.println("AuthWriteWorker: processing client authentication message of length: " + messageBytes.length);
      }

      main.WriteToClientByNetwork(writeToMe,message);
    }
  }
}

