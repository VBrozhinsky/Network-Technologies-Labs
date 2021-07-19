import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static int counter = 1;

    public static void main(String[] args) {
        String ip_addr = CONSTANTS.IP_ADDRESS;
        final Long startDate = (new Date()).getTime();

        if (args.length >= 2) {
            ip_addr = args[CONSTANTS.ARGUMENT_IP];
        }

        try {
            final Map<String, Long> connections = new HashMap<String, Long>(), runningConnections = connections;
            final MulticastSocket server, socket;
            final InetAddress group = InetAddress.getByName(CONSTANTS.IP_ADDRESS);
            server = new MulticastSocket(CONSTANTS.PORT);
            server.joinGroup(group);
            socket = new MulticastSocket();
            socket.joinGroup(group);

            Runnable receiver = new Runnable() {
                public void run() {
                    byte[] buf = new byte[CONSTANTS.BUFFER_SIZE];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    while (true) {
                        try {
                            server.receive(packet);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }runningConnections.put(packet.getSocketAddress().toString(), (new Date()).getTime());
                    }
                }
            };

            Runnable sender = new Runnable() {
                public void run() {
                    DatagramPacket packet = new DatagramPacket(CONSTANTS.MESSAGE0.getBytes(), CONSTANTS.MESSAGE0.length(), group, CONSTANTS.PORT);
                    while (true) {
                        try {
                            socket.send(packet);
                            Thread.sleep(CONSTANTS.SENDER_SLEEP_TIME);
                        } catch (IOException | InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            };

            Runnable updater = new Runnable() {
                public void run() {
                    while (true) {
                        connections.putAll(runningConnections);
                        Map<String, Long> tmp = connections;
                        System.out.println("\n");
                        for (Map.Entry<String, Long> val : tmp.entrySet()) {
                            Date date = new Date();
                            Long currentTime = date.getTime();
                            if (val.getValue() + CONSTANTS.TIME_GAP < currentTime) {
                                connections.remove(val.getKey());
                            }
                            else {
                                if(counter % tmp.size() == 1) {
                                    System.out.println(val.getKey() + CONSTANTS.MESSAGE1);
                                }
                                if(counter % tmp.size() == 2) {
                                    System.out.println(val.getKey() + CONSTANTS.MESSAGE2);
                                }
                                if(counter % tmp.size() == 0) {
                                    System.out.println(val.getKey() + CONSTANTS.MESSAGE3);
                                }
                            }
                        }
                        System.out.println("\n");
                        runningConnections.clear();
                        counter++;
                        try {
                            Thread.sleep(CONSTANTS.UPDATER_SLEEP_TIME);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            };
            new Thread(sender).start();
            new Thread(receiver).start();
            new Thread(updater).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}