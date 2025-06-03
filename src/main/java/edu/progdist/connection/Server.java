package edu.progdist.connection;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Abstração de um servidor que gerencia conexões TCP e multicast.
 */
public abstract class Server {
    protected TcpConnection tcpConnection;                  // trata socket TCP
    protected MulticastConnection multicastConnection;      // trata socket multicast
    protected ExecutorService executor;                     // executor para gerenciar threads
    protected ScheduledExecutorService scheduler;           // scheduler para tarefas periódicas

    public abstract void start(int port);
    protected abstract void run();
    public abstract void stop();
}
