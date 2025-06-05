package edu.progdist.module.drone;

import edu.progdist.connection.Message;
import edu.progdist.connection.TcpConnection;

import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Drone {

    // record que representa a configuração usada para instanciar um drone por região
    private record DroneConfig(String separator, String prefix, String suffix) {}

    private EnviromentData data;
    private String separator;
    private final String prefix;
    private final String suffix;

    public Drone(String separator, String prefix, String suffix) {
        this.data = new EnviromentData();
        this.separator = separator;
        this.prefix = prefix;
        this.suffix = suffix;
    }

    public Drone(int pressao, double radiacao, int temperatura, int umidade,
                 String separator, String prefix, String suffix) {
        this.data = new EnviromentData(pressao, radiacao, temperatura, umidade);
        this.separator = separator;
        this.prefix = prefix;
        this.suffix = suffix;
    }

    public Drone(EnviromentData data, String separator, String prefix, String suffix) {
        this.data = new EnviromentData(data);
        this.separator = separator;
        this.prefix = prefix;
        this.suffix = suffix;
    }

    public EnviromentData getData() {
        return data;
    }

    public void setData(EnviromentData data) {
        this.data = data;
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }

    public void randomize() {
        data.randomize();
    }

    @Override
    public String toString() {
        if (prefix == null) {
            return data.getPressao() + separator +
                   data.getRadiacao() + separator +
                   data.getTemperatura() + separator +
                   data.getUmidade();
        } else {
            return prefix + data.getPressao() + separator +
                   data.getRadiacao() + separator +
                   data.getTemperatura() + separator +
                   data.getUmidade() + suffix;
        }
    }

    public static void main(String[] args) {
        try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10)) {
            Scanner scanner = new Scanner(System.in);
            Random random = new Random();

            Map<String, DroneConfig> droneConfigs = Map.of(
                "norte", new DroneConfig("-", null, null),
                "sul", new DroneConfig(";", "(", ")"),
                "leste", new DroneConfig(",", "{", "}"),
                "oeste", new DroneConfig("#", null, null)
            );

            while (true) {
                System.out.println("Digite a direção do drone (norte, sul, leste, oeste) ou 'sair' para encerrar:");
                String option = scanner.nextLine().toLowerCase();

                if (option.equals("sair")) {
                    System.out.println("Encerrando simulação...");
                    scheduler.shutdownNow();
                    break;
                }

                if (!droneConfigs.containsKey(option)) {
                    System.out.println("Opção inválida. Tente novamente.");
                    continue;
                }

                DroneConfig config = droneConfigs.get(option);
                Drone drone = new Drone(config.separator(), config.prefix(), config.suffix());

                int timer = random.nextInt(2, 6); // entre 2 a 5 segundos

                scheduler.scheduleAtFixedRate(() -> {
                    try {
                        drone.randomize();
                        TcpConnection connection = new TcpConnection("localhost", 8080);
                        connection.send(new Message("DRONE_REQUEST", drone.toString()));
                        connection.close();
                    } catch (IOException e) {
                        System.err.println("Erro ao conectar ao servidor: " + e.getMessage());
                    }
                }, timer, timer, TimeUnit.SECONDS);
            }
        }
    }
}
