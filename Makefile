.PHONY: all clean test compile
all: compile

compile: javamonkey/app/gss/Server.class javamonkey/app/gss/Client.class

clean:
	-rm javamonkey/app/gss/*.class rm security.token

%.class: %.java
	javac $< 

test: clean test_client test_server

test_client: javamonkey/app/gss/Client.class
	echo "make test_client: begin.."
	java javamonkey.app.gss.Client
	echo "make test_client: done."

test_server: javamonkey/app/gss/Server.class
	echo "make test_server: begin.."
	java javamonkey.app.gss.Server
	echo "make test_server: done."
