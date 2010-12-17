.PHONY: all clean test compile killserver waitforkill start_server_socket start_server_nio start_client run_server test_socket test_nio fred saslizedserver saslizedclient
all: compile README.html

compile: GSSizedServer.class GSSizedServerNio.class GSSizedClient.class Hexdump.class FredSasl.class

clean: 
	-rm *.class README.html

killserver:
	echo "killing GSSizedServer java processes.."
	-kill `ps -Ao pid,command | grep java | sed "s/^[ ]*//" | cut -d\  -f1,3 | grep GSSizedServer | cut -d\  -f1`
waitforkill:
	sleep 1

%.class: %.java
	javac -Xlint:unchecked $< 

test: clean
	make test_socket && sleep 5 && make test_nio

#	kill server at end of test.
test_socket: killserver waitforkill start_server_socket start_client
	make killserver

#	kill server at end of test.
test_nio: killserver waitforkill start_server_nio start_client
	make killserver

start_server_socket: GSSizedServer.class
	java GSSizedServer 9000 &
	echo "let kerberized (socket) service come up..."
	sleep 1

start_server_nio: GSSizedServerNio.class
	java GSSizedServerNio 9000 &
	echo "let kerberized (nio) service come up..."
	sleep 1

start_client: GSSizedClient.class
	java GSSizedClient client.properties localhost 9000

README.html: README.md
	Markdown.pl README.md > $@

saslizedserver: SASLizedServer.class
	java SASLizedServer

saslizedclient: SASLizedClient.class
	java SASLizedClient client.properties
