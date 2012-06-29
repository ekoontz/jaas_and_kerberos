# Introduction

This is a set of example code to explain how to use Kerberos with the
JAAS (Java Authentication and Authorization Service) API. The source
code is split into two classes, `KerberizedServer.java` and
`Client.java`. Running `make test` will compile and run them both, at
which time they will set up an authenticated context between them and
print some debugging information.

# Standard Sockets and NIO (Java's New IO)

There are two versions of the server: `KerberizedServer.java`, which
uses the traditional blocking network sockets API, and
`KerberizedServerNIO.java`, which uses NIO. The standard sockets
version is much shorter and easier to understand, so start with
that. However, most real-world Java development seems to use NIO, so I
made a version that uses that. There is also a newer framework called
[Netty](http://jboss.org/netty), built on top of NIO, which I'll be
looking into at some point, and porting the server code to.

# Acknowledgements and Related Work

## [Client/Server Hello World in Kerberos, Java and GSS](http://thejavamonkey.blogspot.com/2008/04/clientserver-hello-world-in-kerberos.html)

A search returned this guide by a blogger who goes by the name Java
Monkey. Java Monkey's approach was exactly what I was looking for:
a "Hello World" application, documented and commented clearly, that
can be studied and understood quickly.

The only drawback, from my point of view, is that it uses the
filesystem for client-server communication: the client logs into
Kerberos and generates a ticket which it writes to a key file. The
server then uses this file to authenticate the client.

However, I wanted to have a Kerberos "Hello World" where the client
and server communicate via a network socket, so that's why I've
created this github repository.

## [Rox Java NIO Tutorial](http://rox-xmlrpc.sourceforge.net/niotut/)

A good guide to learning Java NIO by James Greenfield, as part of his
RoX (RPC over XML) project. Based on his helpful information, I was
able to write a NIO version of my example code after starting with a
traditional sockets implementation.

## [Sun/Oracle official JAAS tutorials](http://java.sun.com/j2se/1.5.0/docs/guide/security/jgss/tutorials/index.html)

I recommend you look at my example code and then only afterwards look
at the Sun/Oracle materials. I found them too complex for tutorial
purposes; they are better as a reference source.

Commenting on these official JAAS documentation articles, Java Monkey writes:

> the code is a socket based client/server which is not useful at all,
> as only a lunatic would be writing their own server communications
> layer in these days of NIO and SOA.

I partially agree with him here. The main problem, from my experience,
of the Sun Tutorial is that it doesn't actually work: the code they
supply simply doesn't function as-is. Also, it uses byte arrays with
an array-length prefix to communicate between the client and server,
which is unnecessarily low-level. I improved this by using standard
sockets but used `Data`{`Input`/`Output`}`Streams` instead of byte
arrays.

One disadvantage of NIO compared to traditional sockets is the API
complexity: compare `KerberizedServer.java` with
`KerberizedServerNIO.java`: the latter is twice as long. (Although, 
`KerberizedServer.java` as written, does not handle more than
one client, whereas `KerberizedServerNIO.java` does, so it's not a
fair comparison).

Another disadvantage of using NIO, however, is that you can't use
`Data`{`Input`/`Output`}`Streams`, unfortunately, as far as I can
tell; would like to be wrong about that.

# Prerequisites

## Kerberos server and client tools

If you're using Debian, install:

* krb5-admin-server
* krb5-kdc

## JDK

This code was tested with the Sun JDK version 1.6.0_22.

## GNU Make

I used GNU Make for development and testing rather than Apache Ant for
simplicity, but an Ant build.xml or a Mavel pom.xml would be good to have here.

# Setup Kerberos Server Infrastructure

## Choose realm name

In my documentation and example configuration files, I use
`FOOFERS.ORG` as my Kerberos realm, and `debian64-3` as the host
running the Kerberos services. Change these based on your preference.

## Edit /etc/krb5.conf

    [libdefaults]
           default_realm = FOOFERS.ORG

    [realms]
           FOOFERS.ORG = {
       		    kdc = debian64-3
                    admin_server = debian64-3
           }
    [domain_realm]
           .foofers.org = FOOFERS.ORG
           foofers.org = FOOFERS.ORG

## Choose principal names.

Choose a principal name for your example client and one for your
example server. Below I use `zookeeperclient` for the client, and
`zookeeperserver` for the server.

## Add principals using kadmin.local 

### Add server principal

We will use `testserver` as the name of the server principal that
`KerberizedServer` and `KerberizedServerNio` uses. It's conventional
to use keytab files, rather than passwords, as Kerberos credentials
for server daemons. This allows a server process to start itself
without manual intervention : no password need be supplied; the server
process simply reads the keytab file and uses this to authenticate with the KDC.

We will therefore use `kadmin.local` to add the server principals
using the `-randkey` option to specify that we don't want to use a
password for server authentication.

On the host on which the KDC runs, do:

     # kadmin.local
     kadmin.local: addprinc -randkey testserver/HOSTNAME
     kadmin.local: ktadd -k /tmp/testserver.keytab testserver/HOSTNAME
     Entry for principal testserver/HOSTNAME with kvno 2, encryption type AES-256 CTS mode with 96-bit SHA-1 HMAC added to keytab WRFILE:/tmp/testserver.keytab.
     Entry for principal testserver/HOSTNAME with kvno 2, encryption type ArcFour with HMAC/md5 added to keytab WRFILE:/tmp/testserver.keytab.
     kadmin.local: (Ctrl-D)

     # scp /tmp/testserver.keytab user@HOSTNAME:~/jaas_and_kerberos

Where `user` is the user who will run `KerberizedServer` and
`KerberizedServerNio`, and `HOSTNAME` is the host on which you will
run them.

 You should add a principal entry for each network interface that your
server will use - otherwise client authentication to
`KerberizedServer` and `KerberizedServerNio` may fail, and you may see
errors in your /var/log/auth.log like :

    Dec 14 14:09:17 debian64-3 krb5kdc[4177]: TGS_REQ (6 etypes {3 1 23 16 17 18}) 192.168.56.1: UNKNOWN_SERVER: authtime 0,  testclient@FOOFERS.ORG for testserver/192.168.0.100@FOOFERS.ORG, Server not found in Kerberos database

To fix this, I added (using `ktadd` as above) a principal for `testserver/192.168.0.100`.

### Add client principal

On the host on which the KDC runs, do:

     # kadmin.local
     kadmin.local: addprinc testclient
     Enter password for principal "testclient@FOOFERS.ORG": 
     Re-enter password for principal "testclient@FOOFERS.ORG": 

See `client.properties` in this directory, which is also shown
below. This assumes you used `clientpassword` as the password in
`kadmin.local` above.

    client.principal.name=zookeeperclient
    client.password=clientpassword
    service.principal.name=testserver

# Test Kerberos Server Infrastructure

## Test server authentication with `kinit -k -t testserver.keytab`
   (these options means use the keytab for authentication rather than
   asking for a password).

     ekoontz@ekoontz:~/jaas$ kinit -k -t testserver.keytab testserver/192.168.0.100
     ekoontz@ekoontz:~/jaas$ 

## Test client authentication with `kinit testclient`

     ekoontz@ekoontz:~/jaas$ cat client.properties 
     client.principal.name=testclient
     client.password=clientpassword
     service.principal.name=testserver
     ekoontz@ekoontz:~/jaas$ kinit testclient
     Please enter the password for testclient@FOOFERS.ORG: 
     ekoontz@ekoontz:~/jaas$ 

# Compile Java example code

Run `make compile`

# Runtime configuration of Java example code

## Server principal in jaas.conf.

See `jaas.conf` in this directory, which is also shown below. Change
`HOSTNAME` to the host that `KerberizedServer` and
`KerberizedServerNio` will run on. Note that we use a single entry
(`KerberizedServer`) for both `KerberizedServer` and
`KerberizedServerNio`.

    Client {
       com.sun.security.auth.module.Krb5LoginModule required
       useTicketCache=false;
    };

    KerberizedServer {
       com.sun.security.auth.module.Krb5LoginModule required
       useKeyTab=false
       keyTab="testserver.keytab"
       useTicketCache=false
       storeKey=true
       principal="testserver/HOSTNAME";
    };

# Test

Run `make test` will start up the example server and run the client
against it. You may run the client against the same server afterwards
by doing `make test_client`. You can kill an existing server process
by doing `make killserver`.
