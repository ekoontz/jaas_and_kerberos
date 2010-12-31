import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;

class ReadWorker implements Runnable {
  protected NIOServerMultiThread main;

  public ReadWorker(NIOServerMultiThread mainObject) {
    main = mainObject;
  }
  
  public void run() {
    System.out.println("starting ReadWorker..");
    
    while(true) {
      // blocks waiting for items to appear on the read queue.
      System.out.println("ReadWorker.run(): waiting for a client to appear in the read queue.");
      SelectionKey readFromMe = main.takeFromReadQueue();
      String message = main.ReadFromClientByNetwork(readFromMe);
      
      if (message == null) {
        System.out.println("ReadWorker: message is null: assuming client hung up.");
        main.CancelClient(readFromMe);
      }
      else {
        if (message.trim().length() == 0) {
          // FIXME: happens after we've just read a message
          // from readFromMe, but still readFromMe is added to queue.
        }
        else {
          System.out.println("ReadWorker:run(): read key : " + readFromMe + " and got message: [" + message + "].");
          main.ProcessClientMessage(readFromMe,message);
        }
      }
    }
  }
}

