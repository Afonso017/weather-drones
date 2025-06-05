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

    public String formatt(String data) {
        if (data == null || data.isEmpty()) {
            return "Error: Invalid Data.";
        }

        StringBuilder sb = new StringBuilder(data);
        char[] array = sb.toString().toCharArray();
        String[] values = new String[4];
        int j = 0;

        for (int i = 0; i < sb.length(); i++) {
            if (Character.isDigit(array[i])) {
                values[j] = values[j].concat(String.valueOf(array[i]));
            } else if (String.valueOf(array[i]).matches("[-,;#]")) {
                j++;
            }
        }

        // Montagem da string formatada
        sb = new StringBuilder();

        sb.append('[').append(values[0]).append("//")
                .append(values[1]).append("//")
                .append(values[2]).append("//")
                .append(values[3]).append(']');

        return sb.toString();
    }

    public static void main(String[] args) {
        // inicia servidor de dados
        Dataserver dataserver = new Dataserver();
    }
}
