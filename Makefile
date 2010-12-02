.PHONY: all clean test compile killserver waitforkill start_server run_client run_server test_socket test_nio
all: compile README.html

compile: KerberizedServer.class KerberizedServerNio.class Client.class Hexdump.class

clean: 
	-rm *.class README.html

killserver:
	echo "killing KerberizedServer java processes.."
	-kill `ps -Ao pid,command | grep java | sed "s/^[ ]*//" | cut -d\  -f1,3 | grep KerberizedServer | cut -d\  -f1`

waitforkill:
	sleep 1

%.class: %.java
	javac -Xlint:unchecked $< 

test: clean
	make test_socket && sleep 5 && make test_nio

test_socket: killserver waitforkill start_server_socket run_client
#	kill server at end of test.
	make killserver

test_nio: killserver waitforkill start_server_nio run_client
#	kill server at end of test.
	make killserver

start_server_socket: KerberizedServer.class
	java KerberizedServer 9000 &
	echo "let kerberized (socket) service come up..."
	sleep 1

start_server_nio: KerberizedServerNio.class
	java KerberizedServerNio 9000 &
	echo "let kerberized (nio) service come up..."
	sleep 1

run_server: killserver KerberizedServer.class
	java KerberizedServer 9000

run_client: Client.class
	java Client client.properties localhost 9000

README.html: README.md
	Markdown.pl README.md > $@
