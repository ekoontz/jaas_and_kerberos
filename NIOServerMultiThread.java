import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

public class NIOServerMultiThread extends NIOServer {

  private class Pair<A,B> {
    public A first;
    public B second;
    
    public Pair(A first, B second) {
      super();
      this.first = first;
      this.second = second;
    }

  }
  

  protected BlockingQueue<SelectionKey> readFromThese;
  protected BlockingQueue<Pair<SelectionKey,String >> writeToThese;

  protected NIOServerMultiThread main;

  private class ReadWorker implements Runnable {
    public ReadWorker(NIOServerMultiThread mainObject) {
      main = mainObject;
    }
    
    public void run() {
      System.out.println("starting ReadWorker..");

      while(true) {
        // blocks waiting for items to appear on the read queue.
        System.out.println("ReadWorker.run(): waiting for keys to read.");
        SelectionKey readFromMe = main.takeFromReadQueue();
        String message = ReadFromClientByNetwork(readFromMe);
                
        if (message == null) {
          System.out.println("ReadWorker: message is null: assuming client hung up.");
          CancelClient(readFromMe);
        }
        else {
          if (message.trim().length() == 0) {
            // FIXME: happens after we've just read a message
            // from readFromMe, but still readFromMe is added to queue.
          }
          else {
            System.out.println("ReadWorker:run(): read key : " + readFromMe + " and got message: [" + message + "].");
            ProcessClientMessage(readFromMe,message);
          }
        }
      }
    }
  }
   
  private class WriteWorker implements Runnable {
    public WriteWorker(NIOServerMultiThread mainObject) {
      main = mainObject;
    }

    public void run() {
      while(true) {
        System.out.println("WriteWorker.run(): waiting for keys to write.");
        // blocks waiting for items to appear on the write queue.
        Pair<SelectionKey,String> messageTuple = main.takeFromWriteQueue();
        SelectionKey writeToMe = messageTuple.first;
        String message = messageTuple.second;
        System.out.println("WriteWorker:run(): writing message: '" + message +"' to " + main.clientNick.get(writeToMe));
        main.WriteToClientByNetwork(writeToMe,message);
      }
    }
  }

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

  protected synchronized void ReadFromClient(SelectionKey sk) {
    // overrides parent ReadFromClient().
    // add to read worker(s)' queue.

    if (readFromThese.contains(sk) == false) {
      System.out.println("Multi: ReadFromClient(): enqueing sk on read queue.");
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
  
  public void StartThreadsAndRun(int localPort) 
    throws IOException {
    readFromThese = new LinkedBlockingQueue<SelectionKey>();
    ReadWorker reader = new ReadWorker(this);

    writeToThese = new LinkedBlockingQueue<Pair<SelectionKey,String>>();
    
    WriteWorker writer = new WriteWorker(this);
    
    new Thread(reader).start();
    new Thread(writer).start();

    // main thread.
    run(localPort);
  }
  

  

}

