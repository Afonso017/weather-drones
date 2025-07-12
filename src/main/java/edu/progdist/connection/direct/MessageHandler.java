package edu.progdist.connection.direct;

@FunctionalInterface
public interface MessageHandler {
    Message handle(Message message);
}
