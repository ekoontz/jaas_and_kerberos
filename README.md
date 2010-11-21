# Introduction

This is a set of example code to explain how to use Kerberos with
the JAAS (Java Authentication and Authorization Service) API.

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

## [Sun/Oracle official JAAS tutorials](http://java.sun.com/j2se/1.5.0/docs/guide/security/jgss/tutorials/index.html)

I recommend you look at my example code and then only afterwards look
at the Sun/Oracle materials. I found them too complex for tutorial
purposes; they are better as a reference source.

Commenting on these official JAAS documentation articles, Java Monkey writes:

> the code is a socket based client/server which is not useful at all,
> as only a lunatic would be writing their own server communications
> layer in these days of NIO and SOA.

I partially agree with him here. The Sun Tutorial uses byte arrays as
the network payload, which does indeed seem old-fashioned. I disagree
however with the idea that network socket-based communication is
outmoded. I elected to use Data{Input/Output}Streams, which provides
an abstraction layer that avoids the low-level byte-array passing. 

# Prerequisites

## Kerberos server and client tools

## JDK

## GNU Make

I used GNU Make for development and testing rather than Apache Ant for
simplicity, but an Ant build.xml or a Mavel pom.xml would be good to have here.

# Setup Kerberos Server Infrastructure

## Choose realm name

## Edit /etc/krb5.conf

## Choose principals

## Add principals using kadmin.local 

# Test Kerberos Server Infrastructure

## test with kinit.

# Compile Java example code

Run 'make compile'

# Runtime configuration of Java example code

## Generate keytab for service principal

## Edit jaas.conf: set KerberizedServer.principal

## Edit server.properties: (FIXME: use keytabs rather than password in file).

## Edit client.properties: (FIXME: prompt for password when client starts rather than password in file).

# Test

Run 'make test' will start up the example server and run the client
against it. You may run the client against the same server afterwards
by doing 'make test_client'. You can kill an existing server process
by doing 'make killserver'.
