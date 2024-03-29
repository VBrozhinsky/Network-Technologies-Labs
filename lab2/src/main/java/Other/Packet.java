package Other;

public class Packet {
    private final int packetLength;
    private final byte[] message;

    public Packet(byte[] message) {
        this.message = message;
        packetLength = message.length;
    }

    public byte[] getBytes() {
        return message;
    }

    public int getPacketLength() {
        return packetLength;
    }
}
