package edu.progdist.connection.direct;

import java.io.IOException;
import java.net.*;

public class MulticastConnection implements Connection {
    private final MulticastSocket ms;
    private final InetSocketAddress group;
    private boolean isClosed = false;

    public MulticastConnection(String groupAddress, int port) throws IOException {
        this.group = new InetSocketAddress(InetAddress.getByName(groupAddress), port);
        this.ms = new MulticastSocket(port);
        ms.joinGroup(group, NetworkInterface.getByName("wlo1"));
    }

    @Override
    public void send(Message message) throws IOException {
        if (!isClosed) {
            byte[] data = message.toString().getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, group);
            ms.send(packet);
            System.out.println("Mensagem enviada: " + message);
        }
    }

    @Override
    public Message receive() throws IOException {
        try {
            byte[] buffer = new byte[1024];
            DatagramPacket pacote = new DatagramPacket(buffer, buffer.length);
            ms.receive(pacote);
            String receivedData = new String(pacote.getData(), 0, pacote.getLength());
            Message message = new Message(receivedData);
            // empacota o endereço e a porta do remetente na mensagem
            return new Message(pacote.getAddress().getHostAddress() + ":" + pacote.getPort(),
                message.toString());
        } catch (IOException e) {
            return new Message("", "");
        }
    }

    @Override
    public void close() throws IOException {
        if (ms != null && !ms.isClosed()) {
            ms.leaveGroup(group, NetworkInterface.getByName("wlo1"));
            ms.close();
            isClosed = true;
        }
    }

    @Override
    public boolean isClosed() {
        return isClosed;
    }
}
