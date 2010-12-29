import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

public class NIOServerMultiThread extends NIOServer {

  protected BlockingQueue<SelectionKey> readFromThese;
  protected BlockingQueue<SelectionKey> writeToThese;

  protected NIOServerMultiThread main;

  private class ReadWorker implements Runnable {

    public ReadWorker(NIOServerMultiThread mainObject) {
      main = mainObject;
    }
    
    public void run() {
      System.out.println("starting ReadWorker..");

      while(true) {
        SelectionKey readFromMe = main.takeFromReadQueue();
        System.out.println("reader got read selection key: " + readFromMe.toString());
        main.ReadFromClientLowLevel(readFromMe);
        
        System.out.println("readworker done.");
      }
    }
  }
   
  private class WriteWorker implements Runnable {
    public void run() {
      // iterate through writeToMe until it's empty.
      return;
    }
  }

  protected SelectionKey takeFromReadQueue() {
    // note: blocking: only should be called from non-main threads
    // like readWorkers.
    System.out.println("takeFromReadQueue() start.");
    while(true) {
      try {
        System.out.println("takeFromReadQueue() of size: " + readFromThese.size());
        
        SelectionKey readFromMe = readFromThese.take();
        System.out.println("takeFromReadQueue got key: " + readFromMe.toString());
        System.out.println("takeFromReadQueue() is now size: " + readFromThese.size());
        return readFromMe;
      }
      catch (InterruptedException e) {
        System.out.println("takeFromReadQueue ignoring InterruptedException and continuing.");
      }
      System.out.println("takeFromReadQueue() end of while loop.");    
    }

  }

  protected void ReadFromClient(SelectionKey sk) {
    // overrides parent ReadFromClient().
    // add to queue.

    if (readFromThese.contains(sk) == false) {
      // FIXME: this is blocking and therefore should not be in main thread.
      try {
        System.out.println("adding new client message selection key: " + sk.toString() + " to read queue.");
        
        readFromThese.put(sk);
      }
      catch (InterruptedException e) {
        System.out.println("ReadFromClient: interrupted and giving up on writing to read queue.");
      }
    }
    else {
      //      System.out.println("not re-adding existing key to queue.");
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
    readFromThese = new LinkedBlockingQueue<SelectionKey>();
    ReadWorker reader = new ReadWorker(this);
    
    new Thread(reader).start();

    // main thread.
    run(localPort);
  }
  

  

}

