package edu.progdist.module.database;

import edu.progdist.connection.Message;
import edu.progdist.connection.Server;
import edu.progdist.connection.TcpConnection;
import edu.progdist.module.drone.EnviromentData;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Banco de dados que armazena os dados climáticos.
 */
public class Database extends Server {
    private final ConcurrentHashMap<AtomicLong, EnviromentData> storage;

    public Database() {
        storage = new ConcurrentHashMap<>();
        executor = Executors.newCachedThreadPool();
    }

    @Override
    public void start(int port) {
        // inicia servidor do banco de dados
        boolean connected = false;
        while (!connected) {
            try {
                tcpConnection = new TcpConnection(port);
                connected = true;
                System.out.println("Banco de dados iniciado na porta " + port);
                run();
            } catch (IOException e) {
                System.err.println("Erro ao iniciar servidor TCP na porta " + port + ": " + e.getMessage());
                System.err.println("Tentando novamente na porta " + (++port) + "...");
            }
        }
    }

    @Override
    protected void run() {
        // aguarda requisições
        executor.submit(() -> {
            while (!tcpConnection.isClosed()) {
                Socket clientSocket = tcpConnection.accept();
                if (clientSocket == null) break;

                executor.submit(() -> tcpConnection.handleClient(clientSocket, (message) -> {
                    switch (message.type()) {
                        case "SAVE_REQUEST" -> {
                            // salva dados climáticos no banco de dados
                            String payload = message.payload();
                            System.out.println("Recebendo dados: " + payload);
                            // TODO: implementar lógica de salvamento
                            return new Message("SAVE_RESPONSE", "Dados salvos com sucesso.");
                        }

                        case "GET_REQUEST" -> {
                            // TODO: implementar envio dos dados
                            return new Message("GET_RESPONSE", "Dados não implementados.");
                        }

                        default -> {
                            return new Message("", "");
                        }
                    }
                }));
            }
        });
    }

    @Override
    public void stop() {

    }

    public static void main(String[] args) {
        // inicia servidor do banco de dados
        Database database = new Database();
    }
}
