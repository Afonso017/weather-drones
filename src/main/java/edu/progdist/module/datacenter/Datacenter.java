package edu.progdist.module.datacenter;

import edu.progdist.connection.Message;
import edu.progdist.connection.MulticastConnection;
import edu.progdist.connection.Server;
import edu.progdist.connection.TcpConnection;

import java.io.IOException;
import java.net.Socket;
import java.util.PriorityQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Servidor que realiza o balanceamento de carga entre múltiplos servidores.
 */
public class Datacenter extends Server {

    // record que representa o endereço de um servidor com sua carga de trabalho
    private record ServerAddress(String host, int workload) implements Comparable<ServerAddress> {
        @Override
        public int compareTo(ServerAddress other) {
            return Integer.compare(this.workload, other.workload);
        }
    }

    private PriorityQueue<ServerAddress> serverAddresses;   // fila de endereços dos servidores

    public Datacenter() {
        executor = Executors.newCachedThreadPool();
        scheduler = Executors.newScheduledThreadPool(1);
    }

    @Override
    public void start(int port) {
        // inicia servidor
        boolean connected = false;
        while (!connected) {
            try {
                tcpConnection = new TcpConnection(port);
                connected = true;
                System.out.println("Servidor TCP iniciado na porta " + port);
            } catch (IOException e) {
                System.err.println("Erro ao iniciar servidor TCP na porta " + port + ": " + e.getMessage());
                System.err.println("Tentando novamente na porta " + (++port) + "...");
            }
        }

        // envia requisição aos servidores via multicast
        try {
            // inicializa fila de endereços dos servidores
            serverAddresses = new PriorityQueue<>();

            multicastConnection = new MulticastConnection("224.6.7.8", 12345);

            // agenda requisição multicast a cada 30 segundos
            scheduler.scheduleAtFixedRate(() -> {
                if (!multicastConnection.isClosed()) {
                    try {
                        multicastConnection.send(new Message("DATACENTER_REQUEST",
                                "Solicitando endereços dos servidores"));
                    } catch (IOException e) {
                        System.err.println("Falha ao enviar multicast: " + e.getMessage());
                    }
                }
            }, 0, 30, TimeUnit.SECONDS);
        } catch (IOException e) {
            System.err.println("Erro ao conectar ao multicast: " + e.getMessage());
        }

        run();
    }

    @Override
    protected void run() {
        // trata conexões tcp
        executor.submit(() -> {
            while (!tcpConnection.isClosed()) {
                // aceita conexões de clientes
                Socket clientSocket = tcpConnection.accept();

                if (clientSocket == null) break;

                // cria uma nova tarefa para lidar com o cliente
                executor.submit(() -> tcpConnection.handleClient(clientSocket, (message) -> {
                    // verifica tipo da mensagem recebida
                    switch (message.type()) {
                        case "USER_REQUEST" -> {   // requisição de usuário
                            // verifica se há servidores disponíveis
                            String payload = serverAddresses.isEmpty()
                                    ? "Nenhum servidor disponível no momento."
                                    : serverAddresses.peek().host();
                            // retorna o endereço do servidor com menor carga
                            return new Message("DATACENTER_RESPONSE", payload);
                        }

                        case "DRONE_REQUEST" -> {  // requisição de drone
                            // trata os dados
                            String payload = message.payload();
                            System.out.println("Recebendo dados do drone: " + payload);
                            return new Message("DATACENTER_RESPONSE", "Dados do drone processados com sucesso.");
                        }

                        default -> {
                            return new Message("DATACENTER_RESPONSE", "");
                        }
                    }
                }));
            }
        });

        // trata conexões multicast
        executor.submit(() -> {
            while (!multicastConnection.isClosed()) {
                try {
                    // recebe mensagem do multicast
                    Message message = multicastConnection.receive();

                    // processa a mensagem e adiciona o servidor à fila
                    if (message.type().equals("SERVER_RESPONSE")) {
                        System.out.println("Mensagem recebida do multicast: " + message);
                        String[] parts = message.payload().split(":");
                        String host = parts[0];
                        int workload = Integer.parseInt(parts[1]);
                        serverAddresses.add(new ServerAddress(host, workload));
                        System.out.println("Servidor adicionado: " + host + " com carga " + workload);
                    }
                } catch (IOException e) {
                    System.err.println("Erro ao processar mensagem do multicast: " + e.getMessage());
                }
            }
        });
    }

    @Override
    public void stop() {
        // encerra todas as tarefas e fecha todas as conexões ao encerrar o datacenter
        try {
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            scheduler.shutdownNow();
            tcpConnection.close();
            multicastConnection.close();
        } catch (IOException e) {
            System.err.println("Erro ao fechar conexões: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("Erro ao finalizar tarefas: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)) {
            Datacenter datacenter = new Datacenter();
            datacenter.start(8080);

            // agenda encerramento do servidor em 3 minutos
            scheduler.schedule(datacenter::stop, 3, TimeUnit.MINUTES);
        }
    }
}
