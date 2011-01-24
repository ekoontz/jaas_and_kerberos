import java.nio.channels.SelectionKey;

public class WriteWorker implements Runnable {
  protected NIOServerMultiThread main;
  
  public WriteWorker(NIOServerMultiThread mainObject) {
    main = mainObject;
  }
  
  public void run() {
    while(true) {
      System.out.println("WriteWorker.run(): waiting for a client to appear in the write queue.");
      // blocks waiting for items to appear on the write queue.
      Pair<SelectionKey,String> messageTuple = main.takeFromWriteQueue();
      SelectionKey writeToMe = messageTuple.first;
      String message = messageTuple.second;
      if (main.clientNick.get(writeToMe) != null) {
        System.out.println("WriteWorker:run(): writing message: '" + message +"' to " + main.clientNick.get(writeToMe));
        main.WriteToClientByNetwork(writeToMe,message);
      }
      else {
        // selection key is null: client has been disconnected; discard this message.
      }
    }
  }
}

