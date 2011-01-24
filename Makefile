.PHONY: all clean kill test compile killchatserver killchatclient start_nio_server_sasl \
restart_nio_server_sasl start_nio_server_sasl_bg run_chat_client 

all: compile README.html

compile: NIOServerSASL.class SASLizedChatClient.class Hexdump.class WriteWorker.class ReadWorker.class AuthReadWorker.class

clean:
	-rm *.class README.html

kill: killchatserver killchatclient

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

killchatclient:
	echo "killing chat client.."
	-kill `jps | cut -d' ' -f1,2 | grep SASLizedChatClient | cut -d' ' -f1,1`

run_chat_client:  SASLizedChatClient.class KeyboardListener.class
	java -cp . SASLizedChatClient client.properties 192.168.56.1 6789

NIOServerSASL.class: Hexdump.class WriteWorker.class NIOServerMultiThread.class NIOServer.class

