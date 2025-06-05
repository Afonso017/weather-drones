package edu.progdist.module.datacenter;

import edu.progdist.connection.Message;
import edu.progdist.connection.MulticastConnection;
import edu.progdist.connection.Server;
import edu.progdist.connection.TcpConnection;

import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Servidor que realiza o balanceamento de carga entre múltiplos servidores.
 */
public class Datacenter extends Server {

    // record que representa o endereço de um servidor com sua carga de trabalho
    private static class ServerAddress implements Comparable<ServerAddress> {
        private final String serverId;  // ID único do servidor
        private final Host host;      // endereço
        private int connectionCount;    // número de conexões ativas
        private double resourceUsage;   // uso de recursos (CPU e RAM)
        private long lastUpdate;        // timestamp em ms

        public ServerAddress(String serverId, Host host, double resourceUsage) {
            this.serverId = serverId;
            this.host = host;
            this.resourceUsage = resourceUsage;
            this.connectionCount = 0;
            this.lastUpdate = System.currentTimeMillis();
        }

        public void update(double resourceUsage, int connectionCount) {
            this.resourceUsage = resourceUsage;
            this.connectionCount = connectionCount;
            this.lastUpdate = System.currentTimeMillis();
        }

        public long getLastUpdate() {
            return lastUpdate;
        }

        public Host host() {
            return host;
        }

        public int getConnectionCount() {
            return connectionCount;
        }

        public void incrementConnectionCount() {
            connectionCount++;
        }

        public double getResourceUsage() {
            return resourceUsage;
        }

        public void setResourceUsage(double resourceUsage) {
            this.resourceUsage = resourceUsage;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ServerAddress sa && this.serverId.equals(sa.serverId);
        }

        @Override
        public int hashCode() {
            return serverId.hashCode();
        }

        @Override
        public int compareTo(ServerAddress other) {
            return Integer.compare(this.connectionCount, other.connectionCount);
        }
    }

    private PriorityQueue<ServerAddress> serverAddresses;   // fila de endereços dos servidores
    private Map<String, ServerAddress> serverMap; // mapa para acesso rápido aos endereços dos servidores

    public Datacenter() {
        executor = Executors.newCachedThreadPool();
        scheduler = Executors.newScheduledThreadPool(2);
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
            // inicializa fila e mapa de endereços dos servidores
            serverMap = new HashMap<>();
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
            }, 10, 30, TimeUnit.SECONDS);
        } catch (IOException e) {
            System.err.println("Erro ao conectar ao multicast: " + e.getMessage());
        }

        // remove servidores que não respondem há mais de 1 minuto
        scheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            serverAddresses.removeIf(s -> {
                boolean expired = now - s.getLastUpdate() > 60_000;
                if (expired) {
                    System.out.println("Removendo servidor inativo: " + s.serverId);
                    serverMap.remove(s.serverId);
                }
                return expired;
            });
        }, 30, 30, TimeUnit.SECONDS);

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
                        case "USER_REQUEST" -> {
                            // retorna o servidor com menos conexões
                            ServerAddress target = leastConnections();
                            String response = (target != null) ? target.host.toString() : "Nenhum servidor disponível.";
                            return new Message("DATACENTER_RESPONSE", response);
                        }

                        case "DRONE_REQUEST" -> {
                            // encaminha a requisição para o servidor com menos uso de recursos via multicast
                            ServerAddress target = lessResourceUsage();
                            if (target != null) {
                                try {
                                    multicastConnection.send(new Message("DRONE_REQUEST",
                                        message.payload()));
                                    return new Message("DATACENTER_RESPONSE",
                                        "Requisição encaminhada para o servidor: " + target.serverId);
                                } catch (IOException e) {
                                    System.err.println("Erro ao enviar requisição multicast: " + e.getMessage());
                                    return new Message("DATACENTER_ERROR", "Erro ao encaminhar requisição.");
                                }
                            } else {
                                return new Message("DATACENTER_ERROR", "Nenhum servidor disponível.");
                            }
                        }

                        default -> {
                            return new Message("DATACENTER_ERROR",
                                "Tipo de mensagem desconhecido: " + message.type());
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
                    Message message = multicastConnection.receive();    // mensagem com endereço e porta do remetente
                    Message request = new Message(message.payload());   // extrai a mensagem original

                    // processa a mensagem e adiciona o servidor à fila
                    if (request.type().equals("SERVER_RESPONSE")) { // extrai informações do payload
                        String[] parts = request.payload().split(";");
                        if (parts.length < 4) {
                            System.err.println("Payload inválido: " + request.payload());
                            continue;
                        }

                        String host = message.toString().split("\\|")[0].split(":")[0]; // endereço do servidor
                        int workload = Integer.parseInt(parts[0]);  // carga de trabalho do servidor
                        double resourceUsage = Double.parseDouble(parts[1]); // uso de recursos do servidor
                        String serverId = parts[2]; // ID do servidor
                        int port = Integer.parseInt(parts[3]); // porta do servidor

                        // verifica se já existe e atualiza ou adiciona
                        Optional<ServerAddress> existing =
                            serverAddresses.stream()
                                .filter(s -> s.serverId.equals(serverId))
                                .findFirst();

                        if (existing.isPresent()) {
                            ServerAddress addr = existing.get();
                            addr.update(resourceUsage, workload);   // atualiza uso de recursos e carga
                            System.out.println("Servidor atualizado: " + serverId + " com carga " + workload +
                                " e uso " + resourceUsage);
                        } else {
                            // adiciona novo servidor à fila e ao mapa
                            ServerAddress addr = new ServerAddress(serverId, new Host(host + ":" + port), resourceUsage);
                            addr.connectionCount = workload;
                            serverMap.put(serverId, addr);
                            serverAddresses.add(addr);
                            System.out.println("Novo servidor adicionado: " + serverId + " com carga " + workload +
                                " e uso " + resourceUsage);
                        }
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
            tcpConnection.close();
            multicastConnection.close();
            executor.shutdown();
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
            scheduler.shutdownNow();
        } catch (IOException e) {
            System.err.println("Erro ao fechar conexões: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("Erro ao finalizar tarefas: " + e.getMessage());
        }
    }

    private synchronized ServerAddress leastConnections() {
        if (serverAddresses.isEmpty()) return null;

        return serverAddresses.stream()
            .min(Comparator.comparingInt(ServerAddress::getConnectionCount))
            .map(server -> {
                server.incrementConnectionCount(); // registra nova conexão
                return server;
            })
            .orElse(null);
    }

    private synchronized ServerAddress lessResourceUsage() {
        if (serverAddresses.isEmpty()) return null;

        return serverAddresses.stream()
            .min(Comparator.comparingDouble(ServerAddress::getResourceUsage))
            .orElse(null);
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
