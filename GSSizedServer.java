import org.ietf.jgss.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.Properties;
import java.security.PrivilegedAction;
import javax.security.auth.Subject;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import java.util.*;

public class GSSizedServer {

     public static void main(String[] args) 
         throws IOException, GSSException {
       // 1. Set Kerberos Properties
       System.setProperty( "sun.security.krb5.debug", "true");
       System.setProperty( "java.security.auth.login.config", "./jaas.conf");
       System.setProperty( "javax.security.auth.useSubjectCredsOnly", "true");

       // 2. Login to the KDC.
       LoginContext loginCtx = null;
       // "Server" refers to a section of the JAAS configuration in the jaas.conf file.
       Subject subject = null;
       try {
         loginCtx = new LoginContext("Server");
         loginCtx.login();
         subject = loginCtx.getSubject();
       }
       catch (LoginException e) {
         System.err.println("Login failure : " + e);
         System.exit(-1);
       }
       // Obtain the command-line arguments and parse the port number

       if (args.length != 1) {
         System.err.println("Usage: java <options> GSSizedServer <localPort>");
         System.exit(-1);
       }

       // 3. Service clients.
       // 3.1. Startup service network connection.
       int localPort = Integer.parseInt(args[0]);
       ServerSocket ss = new ServerSocket(localPort);

       while (true) {

         System.out.println("GSSizedServer: Waiting for client connection...");


         // 3.2 Receive a client connection.
         Socket socket = ss.accept();
         final DataInputStream inStream =
           new DataInputStream(socket.getInputStream());
         final DataOutputStream outStream = 
           new DataOutputStream(socket.getOutputStream());

         System.out.println("GSSizedServer: Got connection from client "
                           + socket.getInetAddress());
	
        // 3.3 get client's security context
        GSSContext clientContext =
          Subject.doAs( subject, new PrivilegedAction<GSSContext>() {
              public GSSContext run() {
                try {
                  GSSManager manager = GSSManager.getInstance();
                  GSSContext context = manager.createContext( (GSSCredential) null);
                  while (!context.isEstablished()) {
                    System.out.println("GSSizedServer: context not yet established: accepting from client.");
                    context.acceptSecContext(inStream,outStream);
                  }
                  
                  return context;
                }
                catch ( Exception e) {
                  e.printStackTrace();
                  return null;
                }
              }
            }
            );
        if (clientContext != null) {
          System.out.println("GSSizedServer: Client authenticated: (principal: " + clientContext.getSrcName() + ")");
          // ..conduct business with client since it's authenticated and optionally encrypted too..
        }

        // TODO: add some actual content passed between client and server.

        System.out.println("GSSizedServer: Closing connection with client "
                           + socket.getInetAddress());
        socket.close();
      }
    }
}
