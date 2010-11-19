import org.ietf.jgss.*;
import java.io.*;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.Properties;

public class SampleServer  {
    
    public static void main(String[] args) 
	throws IOException, GSSException {

      System.setProperty( "sun.security.krb5.debug", "true");


	// Obtain the command-line arguments and parse the port number

	if (args.length != 1) {
	    System.err.println("Usage: java <options> SampleServer <localPort>");
	    System.exit(-1);
	}

	int localPort = Integer.parseInt(args[0]);

	ServerSocket ss = new ServerSocket(localPort);

	GSSManager manager = GSSManager.getInstance();

	while (true) {

	    System.out.println("SampleServer::main() Waiting for incoming connection...");

	    Socket socket = ss.accept();
	    DataInputStream inStream =
		new DataInputStream(socket.getInputStream());
	    DataOutputStream outStream = 
		new DataOutputStream(socket.getOutputStream());

	    System.out.println("SampleServer::main() Got connection from client "
			       + socket.getInetAddress());

            // do stuff...
	    GSSContext context = manager.createContext((GSSCredential)null);

	    // Do the context establishment loop.
	    
	    byte[] token = null;
	    
	    while (!context.isEstablished()) {
              System.out.println("SampleServer::main() context not yet established: accepting from client @ " + socket.getInetAddress());
              
              context.acceptSecContext(inStream,outStream);
	    }
	    
	    System.out.print("Context Established! ");
	    System.out.println("Client is " + context.getSrcName());
	    System.out.println("Server is " + context.getTargName());
	
	    System.out.println("SampleServer::main() Closing connection with client " 
			       + socket.getInetAddress());
	    socket.close();
	}
    }
}
