import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class NIOServerMultiThread extends NIOServer {

  protected BlockingQueue<SelectionKey> readFromThese;
  protected BlockingQueue<Pair<SelectionKey,String >> writeToThese;

  protected SelectionKey takeFromReadQueue() {
    while(true) {
      try { 
        return readFromThese.take();
      }
      catch (InterruptedException e) {
        System.out.println("takeFromReadQueue(): ignoring InterruptedException and continuing.");
      }
    }
  }

  protected Pair<SelectionKey,String> takeFromWriteQueue() {
    while(true) {
      try {
        return writeToThese.take();
      }
      catch (InterruptedException e) {
        System.out.println("takeFromWriteQueue(): ignoring InterruptedException and continuing.");
      }
    }
  }

  @Override 
    protected synchronized void ReadFromClient(SelectionKey sk) {
      // add to queue for ReadWorker (or pool of ReadWorkers).
      if (readFromThese.contains(sk) == false) {
        System.out.println("Multi: ReadFromClient(): enqueing " + clientNick.get(sk)  + " on read queue.");
        System.out.println("read queue is now size: " + readFromThese.size());
        
        try {
          readFromThese.put(sk);
        }
        catch (InterruptedException e) {
          System.out.println("ReadFromClient: interrupted and giving up on put() to read queue.");
        }
      }
  }

  protected void WriteToClient(SelectionKey sk, String message) {
    // overrides parent WriteToClient().
    // add to write worker(s)' queue.

    System.out.println("Multi: WriteToClient(): message: " + message);

    try {
      Pair<SelectionKey,String> messageInfo = new Pair<SelectionKey,String>(sk,message);
      
      writeToThese.put(messageInfo);
    }
    catch (InterruptedException e) {
      System.out.println("WriteToClient: interrupted and giving up on put() to write queue.");
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

  public void setupQueues() {
    readFromThese = new LinkedBlockingQueue<SelectionKey>();
    writeToThese = new LinkedBlockingQueue<Pair<SelectionKey,String>>();
  }
  
  public void StartThreadsAndRun(int localPort) 
    throws IOException {

    setupQueues();

    ReadWorker reader = new ReadWorker(this);
    WriteWorker writer = new WriteWorker(this);
    
    new Thread(reader).start();
    new Thread(writer).start();

    // main thread.
    run(localPort);
  }

  protected boolean ProcessClientMessage(SelectionKey sk,String clientMessage) {
    boolean result = super.ProcessClientMessage(sk,clientMessage);
    return result;
  }

}

