package edu.progdist.module.user;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * HTTP client that connects to the data microservice to get weather
 * information on demand and display it on a dashboard.
 */
public class HTTPUser {

    private static final String SERVICE_HOST = "http://26.44.67.239:8081";
    private static Map<String, List<String>> receivedData = new HashMap<>();
    // Gson instance for JSON conversion
    private static final Gson gson = new Gson();

    /**
     * Initializes the HTTP client process by connecting to the data microservice and
     * displaying a menu for user interaction.
     */
    public static void main(String[] args) {
        HttpClient client = HttpClient.newHttpClient();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n============ MENU ============");
            System.out.println("1. Atualizar dados do servidor");
            System.out.println("2. Mostrar Dashboard");
            System.out.println("3. Sair");
            System.out.print("Escolha uma opção: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    System.out.println("Atualizando com dados do servidor " + SERVICE_HOST + "/data ...");
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(SERVICE_HOST + "/data"))
                        .build();
                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() == 200) {
                            String jsonData = response.body();
                            // Define the type for deserialization
                            Type type = new TypeToken<Map<String, List<String>>>(){}.getType();
                            // Use Gson to parse the JSON string into the map
                            receivedData = gson.fromJson(jsonData, type);
                            System.out.println("Dados atualizados com sucesso.");
                        } else {
                            System.out.println("Falha ao atualizar dados. Código de status: " + response.statusCode());
                        }

                    } catch (IOException | InterruptedException e) {
                        System.err.println("Erro ao se conectar com serviço de dados: " + e.getMessage());
                    }
                    break;

                case "2":
                    if (receivedData.isEmpty()){
                        System.out.println("No data loaded. Use option 1 to fetch data first.");
                    } else {
                        Dashboard.display(receivedData);
                    }
                    break;

                case "3":
                    System.out.println("Exiting...");
                    scanner.close();
                    System.exit(0);
                    break;

                default:
                    System.out.println("Invalid option. Please try again.");
                    break;
            }
        }
    }
}
