package edu.progdist.module.user;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Representa um usuário que se conecta ao broker RabbitMQ do Gateway.
 * Cada instância pode consumir de múltiplos tópicos e exibir um dashboard com os dados coletados.
 */
public class RabbitMQUser {
    private final String host;
    private Connection connection;

    private static final int HISTORY_LIMIT = 100;

    private record RabbitMqEvent(String routingKey, String message) {}

    // histórico de dados
    private static final Queue<RabbitMqEvent> messageHistory = new ConcurrentLinkedQueue<>();
    // dados coletados para o dashboard
    private static Map<String, List<String>> receivedData = new ConcurrentHashMap<>();

    public RabbitMQUser(String host) {
        this.host = host;
    }

    /**
     * Inicia o consumo de mensagens de um tópico específico.
     * Cada chamada a este metodo cria um novo canal e consumidor para o tópico.
     */
    public void start(String topic) {
        try {
            // Reutiliza a mesma conexão se já existir, ou cria uma nova.
            if (connection == null || !connection.isOpen()) {
                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost(host);
                connection = factory.newConnection();
            }
            Channel channel = connection.createChannel();
            channel.exchangeDeclare("weather_data", "topic");

            // fila exclusiva para este consumidor, que será deletada ao fechar
            String queueName = channel.queueDeclare().getQueue();
            channel.queueBind(queueName, "weather_data", topic);

            System.out.println("Iniciou a coleta no tópico de roteamento: " + topic);

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
                String region = delivery.getEnvelope().getRoutingKey();

                // 2. Cria um evento e o adiciona à fila de histórico única.
                messageHistory.add(new RabbitMqEvent(region, message));

                // 3. Garante que a fila não exceda o limite.
                while (messageHistory.size() > HISTORY_LIMIT) {
                    messageHistory.poll();
                }

                // 4. Usa uma stream no histórico para reconstruir o mapa para o dashboard.
                receivedData = messageHistory.stream()
                        .collect(Collectors.groupingBy(
                                RabbitMqEvent::routingKey,
                                Collectors.mapping(RabbitMqEvent::message, Collectors.toList())
                        ));
            };

            channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});

        } catch (Exception e) {
            System.err.println("Erro ao iniciar consumidor para o tópico '" + topic + "': " + e.getMessage());
        }
    }

    /**
     * Para a conexão com o RabbitMQ. Será chamado para todos os consumidores no encerramento.
     */
    public void stop() throws Exception {
        if (connection != null && connection.isOpen()) {
            connection.close();
        }
    }

    public static void main(String[] args) {
        final String rabbitMqHost = "localhost";
        Scanner scanner = new Scanner(System.in);

        // armazena os tópicos já inscritos para evitar duplicatas
        final ConcurrentHashMap.KeySetView<String, Boolean> subscribedTopics = ConcurrentHashMap.newKeySet();

        // uma única instância que gerencia a conexão
        final RabbitMQUser user = new RabbitMQUser(rabbitMqHost);

        // garante que a conexão seja fechada ao encerrar o programa
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                user.stop();
            } catch (Exception e) {
                System.err.println("Erro ao parar o usuário no encerramento.");
            }
        }));

        while (true) {
            System.out.println("\n============ MENU ============");
            System.out.println("Tópicos atuais: " + (subscribedTopics.isEmpty() ? "Nenhum" : subscribedTopics));
            System.out.println("1. Adicionar Coleta de Tópico");
            System.out.println("2. Exibir Dashboard");
            System.out.println("3. Sair");
            System.out.print("Escolha uma opção: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    System.out.print("Digite o tópico para adicionar (ex: '#' para todos, 'norte', 'sul', etc.): ");
                    String topic = scanner.nextLine().toLowerCase();

                    // adiciona o tópico apenas se ele ainda não foi inscrito
                    if (subscribedTopics.add(topic)) {
                        user.start(topic);
                    } else {
                        System.out.println("Já está inscrito neste tópico.");
                    }
                    break;

                case "2":
                    Dashboard.display(receivedData);
                    break;

                case "3":
                    System.out.println("Encerrando...");
                    // shutdown hook irá fechar a conexão
                    System.exit(0);
                    break;

                default:
                    System.out.println("Opção inválida. Tente novamente.");
                    break;
            }
        }
    }
}
