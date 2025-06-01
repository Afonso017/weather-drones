package edu.progdist.connection;

import java.io.IOException;

/**
 * Interface para conexões de rede.
 * Define os métodos básicos para enviar e receber mensagens, além de fechar a conexão.
 */
public interface Connection extends AutoCloseable {
    void send(Message message) throws IOException;
    Message receive() throws IOException;
    void close() throws IOException;
}
