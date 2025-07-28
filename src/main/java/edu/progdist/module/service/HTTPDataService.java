package edu.progdist.module.service;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;

/**
 * Microserviço que consome dados do RabbitMQ e os expõe via uma API HTTP.
 * Este serviço corresponde ao "Serviço - Consumidor - Sob demanda" do diagrama.
 */
public class HTTPDataService {

    private static final Map<String, List<String>> receivedData = new ConcurrentHashMap<>();

    /**
     * Inicia o consumidor RabbitMQ e o servidor HTTP.
     * @param rabbitMqHost O host do RabbitMQ.
     * @param httpPort A porta para o servidor HTTP.
     */
    public void start(String rabbitMqHost, int httpPort, String exchange)
        throws IOException, TimeoutException {

        // configura a conexão com o RabbitMQ para consumir mensagens
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitMqHost);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.exchangeDeclare(exchange, "topic");
        String queueName = channel.queueDeclare().getQueue();

        // ouve todos os tópicos (#) no exchange
        channel.queueBind(queueName, exchange, "#");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            String region = delivery.getEnvelope().getRoutingKey();
            // adiciona a mensagem recebida ao mapa de dados
            receivedData.computeIfAbsent(region, k -> new CopyOnWriteArrayList<>()).add(message);
        };
        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
        System.out.println("Serviço de dados iniciado. Aguardando mensagens do RabbitMQ...");

        // configura e inicia o servidor http
        HttpServer server = HttpServer.create(new InetSocketAddress(httpPort), 0);
        server.createContext("/data", this::handleDataRequest);
        server.setExecutor(null); // usa o executor padrão
        server.start();
        System.out.println("Servidor HTTP iniciado na porta " + httpPort);
    }

    /**
     * Manipulador para requisições HTTP em /data.
     * Serializa o mapa de dados para JSON e o envia como resposta.
     */
    private void handleDataRequest(HttpExchange exchange) throws IOException {
        String jsonResponse = convertMapToJson();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, jsonResponse.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(jsonResponse.getBytes());
        }
    }

    /**
     * Converte manualmente o mapa de dados para uma string JSON.
     */
    private String convertMapToJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean firstRegion = true;
        for (Map.Entry<String, List<String>> entry : HTTPDataService.receivedData.entrySet()) {
            if (!firstRegion) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\": [");
            boolean firstMessage = true;
            for (String message : entry.getValue()) {
                if (!firstMessage) sb.append(",");
                sb.append("\"").append(message.replace("\"", "\\\"")).append("\"");
                firstMessage = false;
            }
            sb.append("]");
            firstRegion = false;
        }
        sb.append("}");
        return sb.toString();
    }

    public static void main(String[] args) {
        try {
            HTTPDataService service = new HTTPDataService();
            service.start("localhost", 8081, "weather_data");
        } catch (IOException | TimeoutException e) {
            System.err.println("Erro ao iniciar o serviço: " + e.getMessage());
        }
    }
}
