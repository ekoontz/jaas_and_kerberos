.PHONY: all clean test compile killserver waitforkill start_server run_client run_server
all: compile

compile: KerberizedServer.class Client.class Hexdump.class

clean: 
	-rm *.class

killserver:
	echo "killing KerberizedServer java processes.."
	-kill `ps -Ao pid,command | grep java | sed "s/^[ ]*//" | cut -d\  -f1,3 | grep KerberizedServer | cut -d\  -f1`

waitforkill:
	sleep 1

%.class: %.java
	javac -Xlint:unchecked $< 

test: killserver waitforkill start_server run_client killserver

start_server: KerberizedServer.class
	java KerberizedServer 9000 &
	echo "let kerberized service come up..."
	sleep 1

runserver: killserver KerberizedServer.class
	java KerberizedServer 9000

run_client: Client.class
	java Client localhost 9000


