package mypackage;

import java.io.IOException;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.AuthorizeCallback;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;

// copied from http://stackoverflow.com/questions/2077768/how-to-use-the-java-sasl-api-and-cram-md5

public class FredSasl {
  
  public static void main(String[] args) throws SaslException {
    
    new MySasl().start();
  }
  
  private static class ClientHandler implements CallbackHandler {
    
    @Override
      public void handle(Callback[] cbs) throws IOException, UnsupportedCallbackException {
      for (Callback cb : cbs) {
        if (cb instanceof NameCallback) {
          
          System.out.println("Client - NameCallback");
          
          NameCallback nc = (NameCallback)cb;
          nc.setName("username");
        } else if (cb instanceof PasswordCallback) {
          
          System.out.println("Client - PasswordCallback");
          
          PasswordCallback pc = (PasswordCallback)cb;
          pc.setPassword("password".toCharArray());
        }
      }
    }
  }
  
  private static class ServerHandler implements CallbackHandler {
    
    @Override
      public void handle(Callback[] cbs) throws IOException, UnsupportedCallbackException {
      for (Callback cb : cbs) {
        if (cb instanceof AuthorizeCallback) {
          
          System.out.println("Server - AuthorizeCallback");
          
          AuthorizeCallback ac = (AuthorizeCallback)cb;
          ac.setAuthorized(true);
          
        } else if (cb instanceof NameCallback) {
          
          System.out.println("Server - NameCallback");
          
          NameCallback nc = (NameCallback)cb;
          nc.setName("username");
          
        } else if (cb instanceof PasswordCallback) {
          
          System.out.println("Server - PasswordCallback");
          
          PasswordCallback pc = (PasswordCallback)cb;
          pc.setPassword("password".toCharArray());
        }
      }
    }
  }
  
  private void start() throws SaslException {
    
    byte[] challenge;
    byte[] response;
    
    ClientHandler clientHandler = new ClientHandler();
    ServerHandler serverHandler = new ServerHandler();
    
    SaslClient sc = Sasl.createSaslClient(new String[] { "CRAM-MD5" }, null, "my_server", "FQHN", null, clientHandler); 
    SaslServer ss = Sasl.createSaslServer("CRAM-MD5", "my_server", "FQHN", null, serverHandler);
    
    challenge = ss.evaluateResponse(new byte[0]);
    response = sc.evaluateChallenge(challenge);
    ss.evaluateResponse(response);
    
    if (ss.isComplete()) {
      System.out.println("Authentication successful.");
    }
  }
}
