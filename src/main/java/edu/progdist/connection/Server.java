package edu.progdist.connection;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Abstração de um servidor que gerencia conexões TCP e multicast.
 */
public abstract class Server {
    public static class Host {
        public String host;
        public int port;

        public Host(String host) {
            String[] parts = host.split(":");
            if (parts.length == 2) {
                this.host = parts[0];
                this.port = Integer.parseInt(parts[1]);
            } else {
                this.host = host;
                this.port = 0;
            }
        }
    }

    protected TcpConnection tcpConnection;                  // trata socket TCP
    protected MulticastConnection multicastConnection;      // trata socket multicast
    protected ExecutorService executor;                     // executor para gerenciar threads
    protected ScheduledExecutorService scheduler;           // scheduler para tarefas periódicas

    public abstract void start(int port);
    protected abstract void run();
    public abstract void stop();
}
