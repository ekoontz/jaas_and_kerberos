import java.io.IOException;
import java.util.HashMap;
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

import java.security.PrivilegedAction;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

// copied from http://stackoverflow.com/questions/2077768/how-to-use-the-java-sasl-api-and-cram-md5

public class FredSasl {
  
  public static void main(String[] args) throws SaslException {
    
    new FredSasl().start();
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

      System.out.println("ServerHandler::handle()");

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

    // 1 Set Kerberos Properties
    System.setProperty( "sun.security.krb5.debug", "true");
    System.setProperty( "java.security.auth.login.config", "./jaas.conf");
    System.setProperty( "javax.security.auth.useSubjectCredsOnly", "true");
    System.setProperty( "javax.security.auth.keyTab","testserver.keytab");
    
    HashMap<String,Object> props = new HashMap<String,Object>();
    props.put(Sasl.QOP, "auth-conf,auth-int,auth");

    SaslServer ss = Sasl.createSaslServer("GSSAPI", "FredSasl", "ekoontz", null, serverHandler);
    //    SaslClient sc = Sasl.createSaslClient(new String[] { "GSSAPI" }, null, "ekoontz", "FQHN", null, clientHandler); 
    /*    SaslClient sc = Sasl.createSaslClient(new String[] { "GSSAPI" }, null, "ekoontz", "FQHN", null, clientHandler); 

    
    challenge = ss.evaluateResponse(new byte[0]);
    response = sc.evaluateChallenge(challenge);
    ss.evaluateResponse(response);
    
    if (ss.isComplete()) {
      System.out.println("Authentication successful.");
    }
    */
  }
}
