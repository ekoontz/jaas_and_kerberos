import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.BlockingQueue;
import java.util.NoSuchElementException;

public class NIOServerMultiThread extends NIOServer {

  protected BlockingQueue<SelectionKey> readFromThese;
  protected BlockingQueue<SelectionKey> writeToThese;

  private class ReadWorker implements Runnable {
    public void run() {

      System.out.println("starting ReadWorker..");

      // iterate through readFromMe until it's empty.
      // NoSuchElementException will be thrown when readFromMe is emptied.
      while(true) {
        try {
          SelectionKey readFromMe = readFromThese.take();
        }
        catch (InterruptedException e) {
          // Finished writing to this client: no more messages in its inbox.
          System.out.println("ReadWorker ignoring InterruptedException and continuing.");
          
        }
      }
        
    }
  }
 
  private class WriteWorker implements Runnable {
    public void run() {

      // iterate through writeToMe until it's empty.
      return;
    }
  }




  public static void main(String[] args) 
    throws IOException {
    // Obtain the command-line arguments and parse the port number
    
    if (args.length != 1) {
      System.err.println("Usage: java <options> NIOServerMultiThread <localPort>");
      System.exit(-1);
    }
    
    int localPort = Integer.parseInt(args[0]);

    NIOServerMultiThread instance = new NIOServerMultiThread();
    try {
      instance.run(localPort);
    }
    catch (IOException e) {
      throw e;
    }
    
  }
  
}

