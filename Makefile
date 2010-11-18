.PHONY: all clean test
all: compile

compile: javamonkey/app/gss/Server.class javamonkey/app/gss/Client.class

clean:
	-rm javamonkey/app/gss/*.class rm security.token

%.class: %.java
	javac $< 

test: clean compile test_client test_server

test_client:
	echo "make test_client: begin.."
	java javamonkey.app.gss.Client
	echo "make test_client: done."

test_server:
	echo "make test_server: begin.."
	java javamonkey.app.gss.Server
	echo "make test_server: done."
