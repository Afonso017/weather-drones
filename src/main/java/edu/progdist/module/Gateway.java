package edu.progdist.module;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import edu.progdist.module.database.Database;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gateway que conecta drones a um broker MQTT e RabbitMQ.
 * Recebe dados dos drones via MQTT, formata-os e publica-os no RabbitMQ e em outro tópico MQTT.
 */
public class Gateway {
    private static final String PREFIX = "[GATEWAY] ";
    private final Database database = new Database();
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    private MqttClient mqttConsumer;
    private MqttClient mqttProducer;
    private Connection rabbitConnection;
    private Channel rabbitChannel;

    public static final String BROKER_MQTT = "tcp://test.mosquitto.org:1883";

    private static final String RABBITMQ_EXCHANGE = "weather_data";
    private static final String MQTT_PRODUCER_TOPIC_PREFIX = "data/realtime/";

    public Gateway(String mqttBroker, String rabbitMqHost) throws Exception {
        setupMqttConsumer(mqttBroker);
        setupMqttProducer(mqttBroker);
        setupRabbitMQ(rabbitMqHost);
    }

    private void setupMqttConsumer(String broker) throws MqttException {
        this.mqttConsumer = new MqttClient(broker, "GatewayConsumer_" + System.currentTimeMillis(),
            new MemoryPersistence());
        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true);
        this.mqttConsumer.connect(connOpts);
        System.out.println(PREFIX + "Conectado ao Broker MQTT como consumidor.");

        this.mqttConsumer.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                System.err.println(PREFIX + "Conexão com MQTT perdida! " + cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                executor.submit(() -> processMessage(topic, new String(message.getPayload())));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {}
        });
        this.mqttConsumer.subscribe("drones/#");
    }

    private void setupMqttProducer(String broker) throws MqttException {
        this.mqttProducer = new MqttClient(broker, MqttClient.generateClientId(), new MemoryPersistence());
        MqttConnectOptions connOpts = new MqttConnectOptions();
        connOpts.setCleanSession(true);
        this.mqttProducer.connect(connOpts);
        System.out.println(PREFIX + "Conectado ao Broker MQTT como produtor.");
    }

    private void setupRabbitMQ(String host) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        this.rabbitConnection = factory.newConnection();
        this.rabbitChannel = this.rabbitConnection.createChannel();
        this.rabbitChannel.exchangeDeclare(RABBITMQ_EXCHANGE, "topic");
        System.out.println(PREFIX + "Conectado ao RabbitMQ e exchange '" + RABBITMQ_EXCHANGE + "' configurada.");
    }

    private void processMessage(String topic, String payload) {
        String region = topic.substring(topic.lastIndexOf("/") + 1);

        String formattedData = formatData(payload);
        if (formattedData == null) {
            System.err.println(PREFIX + "Formato de dados inválido recebido: " + payload);
            return;
        }

        database.saveData(formattedData);
        System.out.println(PREFIX + "Recebido do tópico " + topic + ": " + payload + " formatado como: " + formattedData);

        try {
            rabbitChannel.basicPublish(RABBITMQ_EXCHANGE, region, null,
                formattedData.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println(PREFIX + "Erro ao publicar no RabbitMQ: " + e.getMessage());
        }

        try {
            MqttMessage message = new MqttMessage(formattedData.getBytes());
            mqttProducer.publish(MQTT_PRODUCER_TOPIC_PREFIX + region, message);
        } catch (MqttException e) {
            System.err.println(PREFIX + "Erro ao publicar no MQTT: " + e.getMessage());
        }
    }

    public String formatData(String data) {
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

        sb.append('[').append(values[2]).append("|")
            .append(values[3]).append("|")
            .append(values[0]).append("|")
            .append(values[1]).append(']');

        return sb.toString();
    }

    public void stop() throws Exception {
        if (mqttConsumer != null && mqttConsumer.isConnected()) {
            mqttConsumer.disconnect();
            mqttConsumer.close();
        }
        if (mqttProducer != null && mqttProducer.isConnected()) {
            mqttProducer.disconnect();
            mqttProducer.close();
        }
        if (rabbitChannel != null && rabbitChannel.isOpen()) {
            rabbitChannel.close();
        }
        if (rabbitConnection != null && rabbitConnection.isOpen()) {
            rabbitConnection.close();
        }
        executor.shutdownNow();
        System.out.println(PREFIX + "encerrado.");
    }

    public static void main(String[] args) {
        try {
            final String rabbitMqHost = "localhost";
            Gateway gateway = new Gateway(BROKER_MQTT, rabbitMqHost);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    gateway.stop();
                } catch (Exception e) {
                    System.err.println(PREFIX + "Erro durante o encerramento: " + e.getMessage());
                }
            }));

            System.out.println(PREFIX + "Gateway iniciado.");
        } catch (Exception e) {
            System.err.println(PREFIX + "Falha ao iniciar o Gateway: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
}
