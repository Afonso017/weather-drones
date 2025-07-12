package edu.progdist.module.user;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Dashboard {

    /**
     * Exibe o dashboard com os dados coletados.
     * Mostra o total de dados, dados por região e análise percentual de cada métrica.
     */
    public static void display(Map<String, List<String>> receivedData) {
        System.out.println("\n=====================================================");
        System.out.println("=========== DASHBOARD DE DADOS CLIMÁTICOS ===========");
        System.out.println("=====================================================");
        long totalColetado = receivedData.values().stream().mapToLong(List::size).sum();

        System.out.print("Total de dados coletados: ");
        if (totalColetado == 0) {
            System.out.println("Nenhum dado coletado ainda.");
            System.out.println("=====================================================\n");
            return;
        }
        System.out.println(totalColetado);

        System.out.println("\n============= Distribuição de Leituras ==============");
        receivedData.forEach((region, data) -> {
            double percentage = (double) data.size() / totalColetado * 100.0;
            System.out.printf("\t%-8s: %d registros (%.2f%%)%n", region, data.size(), percentage);
        });

        System.out.println("\n=========== Análise Percentual por Métrica ==========");
        displayPercentageRanking(receivedData, "\tTemperatura", 0);
        System.out.println();
        displayPercentageRanking(receivedData, "\tUmidade Relativa", 1);
        System.out.println();
        displayPercentageRanking(receivedData, "\tPressão do Ar", 2);
        System.out.println();
        displayPercentageRanking(receivedData, "\tRadiação Solar", 3);
        System.out.println("======================================================\n");
    }

    /**
     * Calcula e exibe o ranking percentual de uma métrica específica.
     * Mostra a contribuição de cada região para o total das médias.
     */
    private static void displayPercentageRanking(
        Map<String, List<String>> receivedData, String title, int dataIndex) {
        System.out.println(title + ":");

        // calcula a média de cada região para a métrica
        Map<String, Double> averageByRegion = receivedData.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream()
                    .mapToDouble(data -> parseData(data, dataIndex))
                    .average().orElse(0.0)
            ));

        // calcula o total das médias
        double totalAverageSum = averageByRegion.values().stream().mapToDouble(Double::doubleValue).sum();

        if (totalAverageSum == 0) {
            System.out.println("\t- Dados insuficientes para análise.");
            return;
        }

        // calcula e exibe o percentual de cada região, ordenado
        averageByRegion.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .forEach(entry -> {
                double percentage = (entry.getValue() / totalAverageSum) * 100.0;
                System.out.printf("\t>> %-8s: %.2f%% (média: %.2f)%n", entry.getKey(), percentage, entry.getValue());
            });
    }

    private static double parseData(String data, int index) {
        try {
            String[] parts = data.replace("[", "").replace("]", "").split("\\|");
            return Double.parseDouble(parts[index]);
        } catch (Exception e) { return 0.0; }
    }
}
