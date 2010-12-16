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
import java.security.PrivilegedExceptionAction;
import java.security.Principal;

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
    public void handle(Callback[] callbacks) throws
        UnsupportedCallbackException {
      AuthorizeCallback ac = null;
      for (Callback callback : callbacks) {
        if (callback instanceof AuthorizeCallback) {
          ac = (AuthorizeCallback) callback;
        } else {
          throw new UnsupportedCallbackException(callback,
              "Unrecognized SASL GSSAPI Callback");
        }
      }
      if (ac != null) {
        String authid = ac.getAuthenticationID();
        String authzid = ac.getAuthorizationID();
        if (authid.equals(authzid)) {
          ac.setAuthorized(true);
        } else {
          ac.setAuthorized(false);
        }
        if (ac.isAuthorized()) {
          System.out.println("SASL server GSSAPI callback: setting "
                         + "canonicalized client ID: " + authzid);
          ac.setAuthorizedID(authzid);
        }
      }
    }

  }
  
  private static class ServerHandler implements CallbackHandler {
    
    @Override
    public void handle(Callback[] callbacks) throws
        UnsupportedCallbackException {

      System.out.println("SASL server ServerHandler() starting..");

      AuthorizeCallback ac = null;
      for (Callback callback : callbacks) {
        if (callback instanceof AuthorizeCallback) {
          ac = (AuthorizeCallback) callback;
        } else {
          throw new UnsupportedCallbackException(callback,
              "Unrecognized SASL GSSAPI Callback");
        }
      }
      if (ac != null) {
        String authid = ac.getAuthenticationID();
        String authzid = ac.getAuthorizationID();
        if (authid.equals(authzid)) {
          ac.setAuthorized(true);
        } else {
          ac.setAuthorized(false);
        }
        if (ac.isAuthorized()) {
          System.out.println("SASL server GSSAPI callback: setting "
                         + "canonicalized client ID: " + authzid);
          ac.setAuthorizedID(authzid);
        }
      }
    }
  }
  
  private void start() throws SaslException {
    
    byte[] challenge;
    byte[] response;
    
    ClientHandler clientHandler = new ClientHandler();

    // 1 Set Kerberos Properties
    System.setProperty( "sun.security.krb5.debug", "true");
    
    System.setProperty("javax.security.sasl.level","FINEST");

    System.setProperty( "java.security.auth.login.config", "./jaas.conf");
    System.setProperty( "javax.security.auth.useSubjectCredsOnly", "true");
    System.setProperty( "javax.security.auth.keyTab","testserver.keytab");

    // 2. Login to the KDC.
    LoginContext loginCtx = null;
    // "KerberizedServer" refers to a section of the JAAS configuration in the jaas.conf file.
    Subject subject = null;
    try {
      loginCtx = new LoginContext("FredSasl");
      loginCtx.login();
      subject = loginCtx.getSubject();

      SaslServer ss = null;
      SaslClient sc = null;
      if (subject != null) {
        try {

          System.out.println("<subject details (inner)>");
          for (Principal p: subject.getPrincipals()) {
            System.out.println("classname: " + p.getClass().getName() + " ; subject name : " + p.getName());
          }
          System.out.println("</subject details>");

          ss = Subject.doAs(subject,new PrivilegedExceptionAction<SaslServer>() {
              ServerHandler serverHandler = new ServerHandler();
              public SaslServer run() throws SaslException {
                SaslServer saslServer = null;
                HashMap<String,Object> props = new HashMap<String,Object>();
                //                props.put(Sasl.SERVER_AUTH, "true");
                System.out.println("CREATING SERVER NOW...");
                saslServer = Sasl.createSaslServer("GSSAPI",
                                                   "testserver",
                                                   "ekoontz",props,serverHandler);
                System.out.println("DONE CREATING SERVER.");
                return saslServer;
              }
            });
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    catch (LoginException e) {
      System.err.println("Login failure : " + e);
      System.exit(-1);
    }

    //    challenge = ss.evaluateResponse(new byte[0]);
    /*    response = sc.evaluateChallenge(challenge);
    ss.evaluateResponse(response);
    
    if (ss.isComplete()) {
      System.out.println("Authentication successful.");
      }*/

  }
}
