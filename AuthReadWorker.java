import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

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
      if (clientState == NIOServerSASL.ClientState.Authenticating) {
        byte[] message = main.ReadBytesFromClientByNetwork(readFromMe);
        System.out.println("AuthReadWorker: processing client authentication message of length: " + message.length);
      }
      else {
        // non-authentication case.
        ReadClientMessage(readFromMe);
      }
    }
  }
}

