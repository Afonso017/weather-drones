package edu.progdist.module.user;

import edu.progdist.connection.direct.Message;
import edu.progdist.connection.direct.Server;
import edu.progdist.connection.direct.TcpConnection;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Abstrai as funcionalidades de um usuário.
 */
class TCPUser {
    private TcpConnection tcpConnection;
    private final ScheduledExecutorService scheduler;

    public TCPUser(String host, int port) {
        scheduler = Executors.newScheduledThreadPool(1);
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
        Message response;
        try {
            response = tcpConnection.receive();
            System.out.println("Se conectando ao servidor de dados: " + response);

            // se conecta ao servidor de dados
            String host = response.payload().split(":")[0];
            int port = Integer.parseInt(response.payload().split(":")[1]);
            tcpConnection.connect(host, port);
            tcpConnection.send(new Message("USER_REQUEST", "Usuário conectado ao servidor de dados."));
            System.out.println("Conexão estabelecida com o servidor de dados " + host + " na porta " + port);
        } catch (IOException e) {
            System.err.println("Erro ao receber resposta do datacenter: " + e.getMessage());
            return;
        }

        scheduler.scheduleAtFixedRate(() -> {
            try {
                // envia requisição de dados climáticos ao servidor
                tcpConnection.send(new Message("DATA_REQUEST", ""));

                // recebe a resposta do servidor
                Message dataResponse = tcpConnection.receive();

                if (dataResponse.type().equals("GET_RESPONSE")) {
                    System.out.println("\n\n\nDados climáticos recebidos:");
                    for (String line : dataResponse.payload().split(" ")) {
                        String[] data = line.replace("[", "")
                            .replace("]", "")
                            .split("//");

                        System.out.println("Temperatura: " + data[0] + "°C, " +
                            "Umidade: " + data[1] + "%, " +
                            "Pressão: " + data[2] + "hPa, " +
                            "Radiação: " + data[3] + "W/m²");
                    }
                }
            } catch (IOException e) {
                System.err.println("Erro ao conectar ao servidor: " + e.getMessage());
            } catch (Exception e) {
                System.err.println("Erro ao processar dados recebidos: " + e.getMessage());
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Bem-vindo ao sistema de monitoramento climático!");
        System.out.println("Digite o endereço do datacenter (formato: host:port):");

        Server.Host datacenterHost = new Server.Host(scanner.nextLine());
        new TCPUser(datacenterHost.host, datacenterHost.port);
    }
}
