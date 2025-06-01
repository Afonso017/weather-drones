package edu.progdist.connection;

import java.io.IOException;
import java.net.*;

public class MulticastConnection implements Connection {
    private final MulticastSocket ms;
    private final InetSocketAddress group;

    public MulticastConnection(String groupAddress, int port) throws IOException {
        this.group = new InetSocketAddress(InetAddress.getByName(groupAddress), port);
        this.ms = new MulticastSocket(port);
        ms.joinGroup(group, NetworkInterface.getByName("wlo1"));
    }

    @Override
    public void send(Message message) throws IOException {
        byte[] data = message.toString().getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, group);
        ms.send(packet);
        System.out.println("Mensagem enviada: " + message);
    }

    @Override
    public Message receive() throws IOException {
        byte[] buffer = new byte[1024];
        DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
        ms.receive(pacote);
        String receivedData = new String(pacote.getData(), 0, pacote.getLength());
        return new Message(receivedData);
    }

    @Override
    public void close() throws IOException {
        if (ms != null && !ms.isClosed()) {
            ms.leaveGroup(group, NetworkInterface.getByName("wlo1"));
            ms.close();
        }
    }
}
