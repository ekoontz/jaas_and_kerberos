.PHONY: all clean test compile killserver waitforkill server_start test_client
all: compile

compile: Server.class Client.class

clean: killserver
	-rm *.class

killserver:
	echo "killing KerberizedServer java processes.."
	-kill `ps -Ao pid,command | grep java | sed "s/^[ ]*//" | cut -d\  -f1,3 | grep KerberizedServer | cut -d\  -f1`

waitforkill:
	sleep 1

%.class: %.java
	javac $< 

test: killserver waitforkill server_start test_client killserver

server_start: KerberizedServer.class
	java KerberizedServer 9000 &
	echo "let sample server come up..."
	sleep 1

test_client: Client.class
	echo "make test_client: begin.."
	java Client zookeeperserver localhost 9000
	echo "make test_client: done."

