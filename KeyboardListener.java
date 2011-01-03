import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;

class KeyboardListener implements Runnable {

  private DataOutputStream outStream;

  public KeyboardListener(DataOutputStream chatServerOutput) {
    outStream = chatServerOutput;
  }
  
  public void run() {
    InputStreamReader converter = new InputStreamReader(System.in);
    BufferedReader in = new BufferedReader(converter);
        
    String curLine = "";
    while (true) {
      try {
        curLine = in.readLine();
        outStream.writeBytes(curLine);
        outStream.flush();
      }
      catch (IOException e) {
        System.err.println("Oops, KeyboardListener had an exception.");
        e.printStackTrace();
      }
    }
  }
}

