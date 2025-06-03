package edu.progdist.module.dataserver;

import edu.progdist.connection.Server;

public class Dataserver extends Server {

    public Dataserver() {
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
        // inicia servidor de dados
        Dataserver dataserver = new Dataserver();
    }
}
