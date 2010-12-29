import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

public class NIOServerSASL extends NIOServerMultiThread {
 
  public static void main(String[] args) 
    throws IOException {
    // Obtain the command-line arguments and parse the port number
    
    if (args.length != 1) {
      System.err.println("Usage: java <options> NIOServerSASL <localPort>");
      System.exit(-1);
    }
    
    int localPort = Integer.parseInt(args[0]);

    NIOServerSASL instance = new NIOServerSASL();

    try {
      instance.StartThreadsAndRun(localPort);
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
        Send("you are now authenticated.",sk);
        return true;
      }
      else {
        Send("unrecognized command: " + clientMessage,sk);
      }
    }
    return false;
  }

}

