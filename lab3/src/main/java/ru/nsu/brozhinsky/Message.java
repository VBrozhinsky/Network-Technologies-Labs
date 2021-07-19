package ru.nsu.brozhinsky;

import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Random;

public class Message {
    static final int SIZE_OF_HEADER = Long.BYTES + Integer.BYTES; // guid + type
    private Long GUID;
    private Integer type;
    private byte[] data;
    private Vertex address = null;
    static class TYPES{
        static final int MSG = 1;
        static final int ACK = 2;
        static final int SECOND_PARENT = 3;
        static final int DEAD = 4;
        static final int CONNECT = 5;
        static final int PING = 6;
    }
    private static Random random;
    static {
        random = new Random(System.currentTimeMillis());
    }
    Message(Integer type, byte[] data){
        GUID = random.nextLong();
        this.type = type;
        this.data = data;
    }
    Message(Integer type, byte[] data, Vertex address){
        this(type, data);
        this.address = address;
    }


    Integer getType() {
        return type;
    }

    byte[] getData() {
        return data;
    }

    Message(DatagramPacket packet){
        ByteBuffer buffer = ByteBuffer.allocate(packet.getData().length);
        buffer.put(packet.getData());
        buffer.flip();
        GUID = buffer.getLong();
        type = buffer.getInt();
        data = new byte[packet.getLength() - SIZE_OF_HEADER];
        buffer.get(data);
        address = new Vertex(packet.getAddress(), packet.getPort());
    }

    Long getGUID() {
        return GUID;
    }

    Vertex getAddress() {
        return address;
    }

    DatagramPacket getPacket(){
        ByteBuffer buffer = ByteBuffer.allocate(data.length + SIZE_OF_HEADER);
        buffer.putLong(GUID);
        buffer.putInt(type);
        buffer.put(data);
        buffer.flip(); //можно было не делать тут
        if (address == null){
            return new DatagramPacket(buffer.array(), data.length + SIZE_OF_HEADER);
        }
        else {
            return new DatagramPacket(buffer.array(), data.length + SIZE_OF_HEADER, address.getInetAddress(), address.getPort());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Message message = (Message) o;

        return Objects.equals(GUID, message.GUID);
    }
}
