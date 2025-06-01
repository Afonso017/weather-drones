package edu.progdist.module.database;

import edu.progdist.connection.Server;
import edu.progdist.module.drone.EnviromentData;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Banco de dados que armazena os dados climáticos.
 */
public class Database extends Server {
    private final ConcurrentHashMap<AtomicLong, EnviromentData> storage;

    public Database() {
        storage = new ConcurrentHashMap<>();
    }

    @Override
    public void start(int port) {

    }

    @Override
    protected void run() {

    }

    @Override
    public void stop() {

    }

    public static void main(String[] args) {
        // inicia servidor do banco de dados
        Database database = new Database();
    }
}
