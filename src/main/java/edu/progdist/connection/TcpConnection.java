package edu.progdist.connection;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class TcpConnection implements Connection {
    private Socket socket;
    private ServerSocket serverSocket;
    private BufferedReader in;
    private PrintWriter out;

    public TcpConnection(int port) throws IOException {
        this.serverSocket = new ServerSocket(port);
    }

    public TcpConnection(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    @Override
    public void send(Message message) {
        out.println(message);
    }

    @Override
    public Message receive() throws IOException {
        String rawMessage = in.readLine();
        if (rawMessage == null) {
            throw new IOException("Conexão fechada pelo servidor.");
        }
        return new Message(rawMessage);
    }

    @Override
    public void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }
    }

    public Socket accept() throws IOException {
        return serverSocket.accept();
    }

    public void handleClient(Socket clientSocket) {

    }
}
