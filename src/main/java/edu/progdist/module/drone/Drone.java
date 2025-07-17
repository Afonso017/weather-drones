package edu.progdist.module.drone;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function; // <-- MUDANÇA IMPORTANTE

import static edu.progdist.module.Gateway.BROKER_MQTT;

/**
 * Representa um drone que coleta dados ambientais e os envia para um broker MQTT.
 * O drone é configurado com uma região específica e envia dados formatados periodicamente.
 */
public class Drone {

    private final String prefix;
    private final EnviromentData environmentData = new EnviromentData();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private MqttClient mqttClient;

    public Drone(String region, String mqttBroker, Function<EnviromentData, String> dataFormatter) {
        this.prefix = "[DRONE-" + region + "] ";
        try {
            mqttClient = new MqttClient(mqttBroker, MqttClient.generateClientId(), new MemoryPersistence());
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            connOpts.setAutomaticReconnect(true);
            mqttClient.connect(connOpts);
            System.out.println(prefix + "Conectado ao broker MQTT.");

            Random random = new Random();
            scheduler.scheduleAtFixedRate(() -> sendData(dataFormatter), 0, random.nextInt(2, 6),
                TimeUnit.SECONDS);

        } catch (MqttException e) {
            System.err.println(prefix + "Erro de conexão: " + e.getMessage());
        }
    }

    // formata os dados e envia ao broker MQTT
    private void sendData(Function<EnviromentData, String> dataFormatter) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            System.err.println(prefix + "Não conectado, pulando envio de dados.");
            return;
        }

        environmentData.randomize();

        String payload = dataFormatter.apply(environmentData);

        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(0);

        try {
            System.out.println(prefix + "Enviando: " + payload);
            String topicRegion = prefix.split("-")[1].replace("] ", "");
            mqttClient.publish("drones/" + topicRegion, message);
        } catch (MqttException e) {
            System.err.println(prefix + "Falhou ao enviar dados: " + e.getMessage());
        }
    }

    public void stop() {
        scheduler.shutdown();
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
            }
        } catch (MqttException e) {
            System.err.println(prefix + "Erro ao fechar o drone: " + e.getMessage());
        }
        System.out.println(prefix + "Encerrado.");
    }

    public static void main(String[] args) {
        String region;

        if (args.length < 1) {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Digite a região (norte, sul, leste, oeste): ");
            region = scanner.nextLine().toLowerCase();
            scanner.close();
        } else {
            region = args[0].toLowerCase();
        }

        Map<String, Function<EnviromentData, String>> formatters = Map.of(
            "norte", EnviromentData::toNorthFormat,
            "sul", EnviromentData::toSouthFormat,
            "leste", EnviromentData::toEastFormat,
            "oeste", EnviromentData::toWestFormat
        );

        Function<EnviromentData, String> formatter = formatters.get(region);
        if (formatter == null) {
            System.err.println("Região inválida: " + region);
            return;
        }

        Drone drone = new Drone(region, BROKER_MQTT, formatter);

        Runtime.getRuntime().addShutdownHook(new Thread(drone::stop));
    }
}
