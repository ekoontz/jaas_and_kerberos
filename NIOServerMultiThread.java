import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.NoSuchElementException;

public class NIOServerMultiThread extends NIOServer {

  protected BlockingQueue<SelectionKey> readFromThese;
  protected BlockingQueue<SelectionKey> writeToThese;

  private class ReadWorker implements Runnable {

    BlockingQueue<SelectionKey> readFromTheseInner;

    public ReadWorker(BlockingQueue<SelectionKey> usereadFromThese) {
      readFromTheseInner = usereadFromThese;
    }
    
    public void run() {
      System.out.println("starting ReadWorker..");

      // iterate through readFromThese if it's not empty; 
      // if it's empty, block in take().
      // NoSuchElementException will be thrown when readFromMe is emptied.
      while(true) {
        try {
          SelectionKey readFromMe = readFromTheseInner.take();
          System.out.println("Readworker got key: " + readFromMe.toString());
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
      instance.StartThreadsAndRun(localPort);
    }
    catch (IOException e) {
      throw e;
    }
    
  }
  
  public void StartThreadsAndRun(int localPort) 
    throws IOException {
    ReadWorker reader = new ReadWorker(readFromThese);
    new Thread(reader).start();

    // main thread.
    run(localPort);
  }
  

  

}

