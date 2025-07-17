package edu.progdist.module.user;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Representa um usuário MQTT que se conecta a um broker e assina um tópico específico.
 * Recebe mensagens publicadas nesse tópico e as exibe no console, além de armazenar os dados recebidos
 * para posterior visualização em um dashboard.
 */
public class MQTTUser {
    private MqttClient mqttClient;

    // Define o número máximo de mensagens a serem mantidas no histórico por tópico.
    private static final int HISTORY_LIMIT = 20;

    private record MqttEvent(String topic, String content) {}
    private static final Queue<MqttEvent> messageHistory = new ConcurrentLinkedQueue<>();

    // dados coletados para o dashboard
    private static Map<String, List<String>> receivedData = new ConcurrentHashMap<>();

    public MQTTUser(String broker, String topic) {
        try {
            mqttClient = new MqttClient(broker, "MQTTUser_" + System.currentTimeMillis(), new MemoryPersistence());
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            mqttClient.connect(connOpts);
            System.out.println("Conectado. Tópico assinado: " + topic);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    System.err.println("Conexão perdida. " + cause.getMessage());
                }

                // formata a mensagem recebida e a adiciona ao mapa de dados coletados
                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());

                    String formattedContent = String.format("Região %s: %s%n",
                        topic.substring(topic.lastIndexOf("/") + 1),
                        payload);
                    System.out.printf(formattedContent);

                    messageHistory.add(new MqttEvent(topic, payload));

                    // exibe o histórico de mensagens a cada HISTORY_LIMIT mensagens recebidas
                    if (messageHistory.size() % HISTORY_LIMIT == 0) {
                        receivedData = messageHistory.stream()
                            .collect(Collectors.groupingBy(
                                MqttEvent::topic,
                                Collectors.mapping(MqttEvent::content, Collectors.toList())
                            ));
                        Dashboard.display(receivedData);
                        messageHistory.clear();
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });
            mqttClient.subscribe(topic);
        } catch (MqttException e) {
            System.err.println("Erro: " + e.getMessage());
        }
    }

    public void stop() throws MqttException {
        if (mqttClient != null && mqttClient.isConnected()) {
            mqttClient.disconnect();
            mqttClient.close();
        }

        System.out.println("Programa encerrado.");
        Dashboard.display(receivedData);
    }

    public static void main(String[] args) {
        String topic;

        // se não houver argumentos, solicita o tópico ao usuário
        if (args.length < 1) {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Digite o tópico MQTT " +
                "(ex: # para todos ou uma região (norte, sul, leste e oeste): ");
            topic = "data/realtime/" + scanner.nextLine();
            scanner.close();
        } else {
            topic = args[0];
        }

        final String mqttBroker = "tcp://broker.emqx.io:1883";
        MQTTUser user = new MQTTUser(mqttBroker, topic);

        // adiciona um shutdown hook para garantir que o usuário seja desconectado corretamente
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { user.stop(); } catch (Exception e) { e.printStackTrace(System.err); }
        }));
    }
}
