package edu.progdist.module.dataserver;

import com.sun.management.OperatingSystemMXBean;
import edu.progdist.connection.Message;
import edu.progdist.connection.MulticastConnection;
import edu.progdist.connection.Server;
import edu.progdist.connection.TcpConnection;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.Socket;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Servidor que recebe dados dos drones através do datacenter e envia dados para os usuários via multicast.
 */
public class Dataserver extends Server {

    private final String serverId;
    private int workload; // carga de trabalho do servidor
    private final DecimalFormat df;

    public Dataserver() {
        executor = Executors.newCachedThreadPool();
        scheduler = Executors.newScheduledThreadPool(1);
        workload = 0;

        // formata número com 2 casas decimais
        df = new DecimalFormat("#.##", new DecimalFormatSymbols(Locale.US));

        // gera um id único para o servidor
        serverId = UUID.randomUUID().toString();
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

        // inicia conexão multicast
        try {
            multicastConnection = new MulticastConnection("224.6.7.8", 12345);
        } catch (IOException e) {
            System.err.println("Erro ao iniciar conexão multicast: " + e.getMessage());
            e.printStackTrace(System.err);
            return;
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
                    if (message.type().equals("USER_REQUEST")) { // envia dados do servidor para o usuário
                        workload++; // incrementa carga de trabalho do servidor
                        // TODO: implementar lógica de envio de dados para o usuário
                        return new Message("SERVER_RESPONSE", "");
                    }

                    return new Message("DATASERVER_ERROR",
                        "Tipo de mensagem desconhecido: " + message.type());
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

                    // envia resposta ao datacenter
                    if (request.type().equals("DATACENTER_REQUEST")) {
                        Message response = new Message("SERVER_RESPONSE",
                            workload + ";" + getResourceUsage() + ";" + serverId);
                        multicastConnection.send(response);
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

    private String getResourceUsage() {
        // uso de cpu
        OperatingSystemMXBean os =
            (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        double cpuLoad = os.getCpuLoad();

        // uso de memória
        long totalMemory = os.getTotalMemorySize();
        long freeMemory = os.getFreeMemorySize();
        long usedMemory = totalMemory - freeMemory;

        // calcula porcentagem de uso de recursos e faz a média
        double cpu = os.getCpuLoad() * 100;
        double mem = ((double) usedMemory / totalMemory) * 100;
        double resourceUsage = (cpu + mem) / 2;

        return df.format(resourceUsage); // formata número com 2 casas decimais
    }

    public static void main(String[] args) {
        try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)) {
            Dataserver dataserver = new Dataserver();
            dataserver.start(8080);

            // agenda encerramento do servidor em 3 minutos
            scheduler.schedule(dataserver::stop, 3, TimeUnit.MINUTES);
        }
    }
}
