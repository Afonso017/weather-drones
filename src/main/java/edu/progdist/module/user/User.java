package edu.progdist.module.user;

import edu.progdist.connection.Message;
import edu.progdist.connection.TcpConnection;

import java.io.IOException;

/**
 * Abstrai as funcionalidades de um usuário.
 */
class User {
    private TcpConnection tcpConnection;

    public User(String host, int port) {
        try {
            this.tcpConnection = new TcpConnection(host, port);
            System.out.println("Conexão TCP estabelecida com o servidor " + host + " na porta " + port);
            run();
        } catch (IOException e) {
            System.err.println("Erro ao conectar ao servidor: " + e.getMessage());
        }
    }

    private void run() {
        System.out.println("Enviando mensagem de teste...");
        try {
            // Envia uma mensagem de teste
            tcpConnection.send(new Message("CLIENT_REQUEST", ""));
            System.out.println("Mensagem enviada com sucesso.");
        } catch (Exception e) {
            System.err.println("Erro ao enviar mensagem: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new User("127.0.0.1", 8080);
    }
}
