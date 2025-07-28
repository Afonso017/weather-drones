package edu.progdist.module.dataserver;

import com.sun.management.OperatingSystemMXBean;
import edu.progdist.connection.direct.Message;
import edu.progdist.connection.direct.MulticastConnection;
import edu.progdist.connection.direct.Server;
import edu.progdist.connection.direct.TcpConnection;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.Socket;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Scanner;
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
    private final Host databaseHost;
    private int port;

    public Dataserver(Host databaseHost) {
        this.databaseHost = databaseHost;
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

        this.port = port; // armazena a porta do servidor

        // inicia conexão multicast
        try {
            multicastConnection = new MulticastConnection("224.6.7.8", 12345);
        } catch (IOException e) {
            System.err.println("Erro ao iniciar conexão multicast: " + e.getMessage());
            e.printStackTrace(System.err);
            return;
        }

        // se conecta ao banco de dados
        try {
            tcpConnection.connect(databaseHost.host, databaseHost.port);
        } catch (IOException e) {
            System.err.println("Erro ao conectar ao banco de dados: " + e.getMessage());
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
                    switch (message.type()) {
                        case "USER_REQUEST" -> {
                            workload++; // incrementa carga de trabalho do servidor
                            return new Message("", "");
                        }

                        case "DATA_REQUEST" -> {
                            // envia requisição para o banco de dados
                            tcpConnection.send(new Message("GET_DATA", ""));
                            Message dataResponse;
                            try {
                                dataResponse = tcpConnection.receive();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            return dataResponse;
                        }

                        case "DATA_RESPONSE" -> {
                            // recebe dados do banco de dados
                            String data = message.payload();

                            // envia resposta ao usuário
                            return new Message("DATA_RESPONSE", data);
                        }
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
                    switch (request.type()) {
                        case "DATACENTER_REQUEST" -> {
                            Message response = new Message("SERVER_RESPONSE",
                                workload + ";" + getResourceUsage() + ";" + serverId + ";" + port);
                            multicastConnection.send(response);
                        }

                        case "DRONE_REQUEST" -> {
                            // trata os dados
                            String data = request.payload();
                            System.out.println("Dados recebidos: " + data);
                            String formattedData = format(data);

                            System.out.println("Dados formatados: " + formattedData);

                            // envia para o banco de dados
                            Message response = new Message("SAVE_DATA", formattedData);
                            System.out.println("Enviando dados para o banco de dados: " + response);
                            tcpConnection.send(response);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Erro ao processar mensagem do multicast: " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Erro inesperado ao processar mensagem do multicast: " + e.getMessage());
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

    public String format(String data) {
        if (data == null || data.isEmpty()) {
            return "Error: Invalid Data.";
        }

        StringBuilder sb = new StringBuilder(data);
        char[] array = sb.toString().toCharArray();
        String[] values = { "", "", "", "" };
        int j = 0;

        for (int i = 0; i < sb.length(); i++) {
            if (Character.isDigit(array[i]) || array[i] == '.' || (array[i] == '-' && !Character.isDigit(array[i - 1]))) {
                values[j] = values[j].concat(String.valueOf(array[i]));
            } else if (String.valueOf(array[i]).matches("[-,;#]")) {
                j++;
            }
        }

        // Montagem da string formatada
        sb = new StringBuilder();

        sb.append('[').append(values[0]).append("//")
                .append(values[1]).append("//")
                .append(values[2]).append("//")
                .append(values[3]).append(']');

        return sb.toString();
    }

    public static void main(String[] args) {
        try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)) {
            Scanner scanner = new Scanner(System.in);
            System.out.println("Digite o endereço do banco de dados (host:port):");
            Dataserver dataserver = new Dataserver(new Host(scanner.nextLine()));
            dataserver.start(8080);

            // agenda encerramento do servidor em 3 minutos
            scheduler.schedule(dataserver::stop, 3, TimeUnit.MINUTES);
        }
    }
}
