package edu.progdist.connection;

@FunctionalInterface
public interface MessageHandler {
    Message handle(Message message);
}
