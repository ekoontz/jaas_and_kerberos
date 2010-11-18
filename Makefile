.PHONY: all clean test
all: compile

compile: javamonkey/app/gss/Server.class javamonkey/app/gss/Client.class

clean:
	-rm javamonkey/app/gss/*.class rm security.token

%.class: %.java
	javac $< 

test: clean compile
	java javamonkey.app.gss.Client
	java javamonkey.app.gss.Server
