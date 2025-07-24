package edu.progdist.module.user;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente HTTP que se conecta ao microserviço de dados para obter informações
 * climáticas sob demanda e exibi-las em um dashboard.
 */
public class HTTPUser {

    private static final String SERVICE_HOST = "http://localhost:8081";
    private static Map<String, List<String>> receivedData = new HashMap<>();

    /**
     * Analisador simples para a string JSON retornada pelo serviço de dados.
     * @param json A string contendo os dados em formato JSON.
     * @return Um mapa com os dados organizados por região.
     */
    public static Map<String, List<String>> parseJsonToMap(String json) {
        Map<String, List<String>> map = new HashMap<>();
        // regex para extrair: "região": ["msg1", "msg2", ...]
        Pattern pattern = Pattern.compile("\"(.*?)\":\\s*\\[(.*?)]");
        Matcher matcher = pattern.matcher(json.substring(1, json.length() - 1));

        while (matcher.find()) {
            String region = matcher.group(1);
            String messagesJson = matcher.group(2);
            List<String> messages = new ArrayList<>();
            if (!messagesJson.isEmpty()) {
                // separa as mensagens
                String[] messageArray = messagesJson.split("\",\"");
                for (String msg : messageArray) {
                    // remove aspas do início e fim de cada mensagem
                    messages.add(msg.replaceAll("^\"|\"$", ""));
                }
            }
            map.put(region, messages);
        }
        return map;
    }

    /**
     * Inicializa o processo do cliente HTTP conectando ao microservice de dados e exibe um menu para o usuário interagir.
     */
    public static void main(String[] args) {
        HttpClient client = HttpClient.newHttpClient();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n============ MENU ============");
            System.out.println("1. Atualizar Dados do Servidor");
            System.out.println("2. Exibir Dashboard");
            System.out.println("3. Sair");
            System.out.print("Escolha uma opção: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    System.out.println("Buscando dados do serviço em " + SERVICE_HOST + "/data ...");
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SERVICE_HOST + "/data"))
                        .build();
                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() == 200) {
                            String jsonData = response.body();
                            receivedData = parseJsonToMap(jsonData);
                            System.out.println("Dados atualizados com sucesso.");
                        } else {
                            System.out.println("Falha ao buscar dados. Código de status: " + response.statusCode());
                        }

                    } catch (IOException | InterruptedException e) {
                        System.err.println("Erro ao conectar ao serviço de dados");
                    }
                    break;

                case "2":
                    if (receivedData.isEmpty()){
                        System.out.println("Nenhum dado foi carregado. Use a opção 1 para buscar os dados primeiro.");
                    } else {
                        Dashboard.display(receivedData);
                    }
                    break;

                case "3":
                    System.out.println("Encerrando...");
                    System.exit(0);
                    break;

                default:
                    System.out.println("Opção inválida. Tente novamente.");
                    break;
            }
        }
    }
}
