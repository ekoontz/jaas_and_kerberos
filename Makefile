.PHONY: all clean test compile killserver waitforkill \
 start_server_socket start_server_nio start_client \
 run_server test_socket test_nio fred saslizedserver \
 saslizedclient testsasl killsaslizedserver nio_server \
 nio_server_multi chat_client nio_server_sasl

all: compile README.html

compile: NIOServerSASL.class SASLizedChatClient.class Hexdump.class 

clean: 
	-rm *.class README.html

README.html: README.md
	Markdown.pl README.md > $@

%.class: %.java
	javac -Xlint:unchecked $< 

test: clean
	make start_nio_server_sasl_bg run_chat_client killchatserver

start_nio_server_sasl: NIOServerSASL.class 
	java NIOServerSASL 6789 

restart_nio_server_sasl: NIOServerSASL.class killchatserver
	java NIOServerSASL 6789 

start_nio_server_sasl_bg: NIOServerSASL.class killchatserver
	java NIOServerSASL 6789 &
	echo "started server; giving it a chance to initialize and listen on port 6789.."
	sleep 5

killchatserver:
	echo "killing chat server.."
	-kill `jps | cut -d' ' -f1,2 | grep NIOServerSASL | cut -d' ' -f1,1`

run_chat_client:  SASLizedChatClient.class KeyboardListener.class
	java -cp . SASLizedChatClient client.properties 192.168.56.1 6789

NIOServerSASL.class: Hexdump.class WriteWorker.class NIOServerMultiThread.class NIOServer.class

