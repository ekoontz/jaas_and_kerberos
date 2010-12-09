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
an array-length prefix to communicate between the client and sever,
which is unnecessarily low-level. I improved this by using standard
sockets but used `Data`{`Input`/`Output`}`Streams` instead of byte
arrays.

Using NIO, which allows for non-blocking networking seems to make the
code more complex (compare `KerberizedServer.java` with `KerberizedServerNIO.java`), but as Java Monkey alludes to with his comment
about "these days of NIO", you'll likely want to use NIO these
days for performance reasons (with traditional sockets, you'll have to
create a separate thread to handle each client, which won't scale).

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

See the file `listprincs` in this directory for the list of principals
in my test Kerberos system.

# Test Kerberos Server Infrastructure

## test with kinit.

# Compile Java example code

Run `make compile`

# Runtime configuration of Java example code

## Generate keytab for service principal

(FIXME: currently we use a password (see below)). Instead it would be better to use a keytab.

## Server principal in jaas.conf.

See `jaas.conf` in this directory, which is also shown below:

    Client {
       com.sun.security.auth.module.Krb5LoginModule required
       useTicketCache=false;
    };

    KerberizedServer {
       com.sun.security.auth.module.Krb5LoginModule required
       useKeyTab=false
       storeKey=true
       useTicketCache=false
       principal="zookeeperserver/debian64-3";
    };

## Set server password in `server.properties`: (FIXME: use keytabs rather than password in file).

See `server.properties` in this directory, which is also shown below:

    service.password=serverpassword

## Set client password in `client.properties`: (FIXME: rely on user running `kinit` prior to `zkClient.sh` usage).

See `client.properties` in this directory, which is also shown below:

    client.principal.name=zookeeperclient
    client.password=clientpassword
    service.principal.name=zookeeperserver@debian64-3

# Test

Run 'make test' will start up the example server and run the client
against it. You may run the client against the same server afterwards
by doing 'make test_client'. You can kill an existing server process
by doing 'make killserver'.
