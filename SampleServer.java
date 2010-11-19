/*
 * @(#)SampleServer.java
 *
 * Copyright 2001-2002 Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or 
 * without modification, are permitted provided that the following 
 * conditions are met:
 * 
 * -Redistributions of source code must retain the above copyright  
 * notice, this  list of conditions and the following disclaimer.
 * 
 * -Redistribution in binary form must reproduct the above copyright 
 * notice, this list of conditions and the following disclaimer in 
 * the documentation and/or other materials provided with the 
 * distribution.
 * 
 * Neither the name of Oracle and/or its affiliates. or the names of 
 * contributors may be used to endorse or promote products derived 
 * from this software without specific prior written permission.
 * 
 * This software is provided "AS IS," without a warranty of any 
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND 
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY 
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY 
 * DAMAGES OR LIABILITIES  SUFFERED BY LICENSEE AS A RESULT OF  OR 
 * RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR 
 * ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE 
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, 
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER 
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF 
 * THE USE OF OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN 
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 * 
 * You acknowledge that Software is not designed, licensed or 
 * intended for use in the design, construction, operation or 
 * maintenance of any nuclear facility. 
 */

import org.ietf.jgss.*;
import java.io.*;
import java.net.Socket;
import java.net.ServerSocket;

/**
 * A sample server application that uses JGSS to do mutual authentication
 * with a client using Kerberos as the underlying mechanism. It then
 * exchanges data securely with the client.
 *
 * Every message exchanged with the client includes a 4-byte application-
 * level header that contains the big-endian integer value for the number
 * of bytes that will follow as part of the JGSS token.
 *
 * The protocol is:
 *    1.  Context establishment loop:
 *         a. client sends init sec context token to server
 *         b. server sends accept sec context token to client
 *         ....
 *    2. client sends a wrap token to the server.
 *    3. server sends a mic token to the client for the application
 *       message that was contained in the wrap token.
 */

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

	    System.out.println("SampleServer::main() Closing connection with client " 
			       + socket.getInetAddress());
	    socket.close();
	}
    }
}
