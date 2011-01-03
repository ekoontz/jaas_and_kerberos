import java.nio.channels.SelectionKey;

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
      ReadClientMessage(readFromMe);
    }
  }

  protected void ReadClientMessage(SelectionKey readFromMe) {
    if (!readFromMe.isValid()) {
      System.out.println("key is not valid: assuming client hung up.");
      main.CancelClient(readFromMe);
      return;
    }

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

