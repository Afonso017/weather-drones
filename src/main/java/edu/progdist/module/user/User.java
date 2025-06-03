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
        // envia requisição ao servidor
        try {
            tcpConnection.send(new Message("USER_REQUEST", ""));
            System.out.println("Mensagem enviada com sucesso.");
        } catch (Exception e) {
            System.err.println("Erro ao enviar mensagem: " + e.getMessage());
        }

        // recebe a resposta do servidor
        try {
            Message response = tcpConnection.receive();
            System.out.println("Resposta recebida do servidor: " + response);
        } catch (IOException e) {
            System.err.println("Erro ao receber resposta do servidor: " + e.getMessage());
        } finally {
            try {
                tcpConnection.close();
                System.out.println("Conexão fechada.");
            } catch (IOException e) {
                System.err.println("Erro ao fechar conexão: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        new User("127.0.0.1", 8081);
    }
}
