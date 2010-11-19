.PHONY: all clean test compile
all: compile

compile: javamonkey/app/gss/Server.class javamonkey/app/gss/Client.class SampleServer.class

clean:
	-rm javamonkey/app/gss/*.class *.class rm security.token
	-kill `ps -Ao pid,command | grep java | sed "s/^[ ]*//" | cut -d\  -f1,3 | grep SampleServer | cut -d\  -f1`

%.class: %.java
	javac $< 

test: clean test_client test_server

test_client: javamonkey/app/gss/Client.class SampleServer.class
	echo "make test_client: begin.."
	java SampleServer 9000 &
	echo "let sample server come up..."
	sleep 2
	java javamonkey.app.gss.Client zookeeperserver localhost 9000
	echo "make test_client: done."

test_server: javamonkey/app/gss/Server.class
	echo "make test_server: begin.."
	java javamonkey.app.gss.Server
	echo "make test_server: done."
