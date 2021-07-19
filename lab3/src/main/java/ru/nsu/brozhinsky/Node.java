package ru.nsu.brozhinsky;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Node {
    private static final int MAX_MESSAGE_SIZE = 4096;
    private static final int WAIT_MESSAGES = 5;
    private static final int AMOUNT_OF_MESSAGES = 5;
    private static final int TIMEOUT = 3000;
    private static final int TIMELIVE = 10000;
    private static final int INET_ADDRESS_MAX_SIZE = 6;
    private Vertex parent = null;
    private Vertex heir = null;
    private Vertex secondParent = null;
    private DatagramSocket ds = null;
    private Map<Message, Long> waitAnswer;//messages, waiting ACK
    private List<Message> allMessages;//history
    private LinkedHashMap<Vertex, Long> children;
    private String login;
    private int port;
    private int loss;
    private Random rand;
    private long parentActivityTime;

    private Node(String login, int loss, int port) {
        this.login = login;
        this.port = port;
        this.loss = loss;
        rand = new Random(System.currentTimeMillis());
        waitAnswer = new ConcurrentHashMap<>(); //обеспечивает параллелизм
        children = new LinkedHashMap<>();
        allMessages = new LinkedList<>();
    }

    private Node(String login, int loss, int port, InetAddress inetAddress, int parentPort) {
        this(login, loss, port);
        this.parent = new Vertex(inetAddress, parentPort);
    }

    public static void main(String[] args) {
        Node node;
        try {
            switch (args.length) {
                case 3:
                    node = new Node(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]));
                    break;
                case 5:
                    node = new Node(args[0], Integer.parseInt(args[1]), Integer.parseInt(args[2]), InetAddress.getByName(args[3]), Integer.parseInt(args[4]));
                    break;
                default:
                    System.out.println("Wrong number of arguments");
                    return;
            }
            node.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void start() throws RuntimeException, IOException {
        try {
            ds = new DatagramSocket(port);
            ds.setSoTimeout(400);
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }

        if (parent != null) {
            connectToParent();
        }

        Console console = System.console();
        BufferedReader streamStdin;

        if (console != null) {
            streamStdin = new BufferedReader(console.reader());
        } else {
            streamStdin = new BufferedReader(new InputStreamReader(System.in));
        }

        System.out.println(ds.getInetAddress());

        long lastTime = System.currentTimeMillis();
        DatagramPacket packIn = new DatagramPacket(new byte[MAX_MESSAGE_SIZE], MAX_MESSAGE_SIZE);
        //packIn.setAddress(InetAddress.getByName("127.0.0.1"));
        while (true) {
            if (System.currentTimeMillis() - lastTime > TIMEOUT) {
                lastTime = System.currentTimeMillis();
                broadcast(new Message(Message.TYPES.PING, new byte[0]));
                if (!children.isEmpty()) {
                    if (parent != null) {
                        heir = parent;
                    } else if (children.entrySet().iterator().hasNext()) {
                        Map.Entry<Vertex, Long> entry = children.entrySet().iterator().next();
                        heir = entry.getKey();
                    }
                    checkChildren();
                }
                System.out.println(packIn.getSocketAddress().toString());
                if (parent != null) {
                    if (System.currentTimeMillis() - parentActivityTime > TIMELIVE) { //смерть родителя
                        Vertex dead = parent;
                        String tmp = parent.getPort() + " has disconnected";
                        System.out.println(tmp);
                        parent = secondParent;
                        secondParent = null;
                        if (parent != null) {
                            ByteBuffer buffer = ByteBuffer.allocate(dead.getInetAddress().getAddress().length + Integer.BYTES * 2 + tmp.getBytes().length);
                            buffer.putInt(dead.getPort());
                            buffer.putInt(dead.getInetAddress().getAddress().length);
                            buffer.put(dead.getInetAddress().getAddress());
                            buffer.put(tmp.getBytes(Charset.forName("UTF-8")));
                            for (Map.Entry<Vertex, Long> mapEntry : children.entrySet()) {
                                sendMessage(new Message(Message.TYPES.DEAD, buffer.array(), mapEntry.getKey()));
                            }
                            //System.out.println("my parent when last parent dead " + parent.getPort());
                            heir = parent;
                            connectToParent();
                        } else if (children.entrySet().iterator().hasNext()) {
                            Map.Entry<Vertex, Long> entry = children.entrySet().iterator().next();
                            heir = entry.getKey();
                            createSecondParent(tmp, heir);
                        }
                    }
                }
            }

            System.out.println(packIn.getSocketAddress().toString());

            if (streamStdin.ready()) { // Есть ли данные на stdin (не блокирующий вызов)
                String tmp = streamStdin.readLine();
                broadcast(new Message(Message.TYPES.MSG, (login + " : " + tmp).getBytes(Charset.forName("UTF-8"))));
                System.out.println((login + " : " + tmp));
            }
            try {
                ds.receive(packIn); // Ждём сообщений по сокету (блокирующий на TIMEOUT)
                if (rand.nextInt(100) >= loss) {
                    Message newMessage = new Message(packIn);
                    receiveMessage(newMessage);
                    if (newMessage.getType() != Message.TYPES.PING) {
                        sendAnswer(newMessage);
                    }

                } else {
                    System.out.println("lost");
                }
            } catch (SocketTimeoutException e) {
                resendMessages();
            }
            System.out.println(packIn.getSocketAddress().toString());
        }

    }

    private void createSecondParent(String tmp, Vertex parent) {
        ByteBuffer buffer = ByteBuffer.allocate(parent.getInetAddress().getAddress().length + Integer.BYTES * 2 + tmp.getBytes().length);
        buffer.putInt(parent.getPort());
        buffer.putInt(parent.getInetAddress().getAddress().length);
        buffer.put(parent.getInetAddress().getAddress());
        buffer.put(tmp.getBytes(Charset.forName("UTF-8")));
        for (Map.Entry<Vertex, Long> mapEntry : children.entrySet()) {
            if(!parent.getInetAddress().equals(mapEntry.getKey().getInetAddress())) {
                sendMessage(new Message(Message.TYPES.SECOND_PARENT, buffer.array(), mapEntry.getKey()));
            }
        }
    }

    private void connectToParent() throws IOException {
        Message connectMessage = new Message(Message.TYPES.CONNECT, new byte[0], parent);
        sendMessage(connectMessage);

        //System.out.println("my parent " + parent.getPort());
        DatagramPacket recivedPack = new DatagramPacket(new byte[Message.SIZE_OF_HEADER + Long.BYTES + Integer.BYTES * 2 + INET_ADDRESS_MAX_SIZE + Long.BYTES], Message.SIZE_OF_HEADER + Long.BYTES + Integer.BYTES * 2 + INET_ADDRESS_MAX_SIZE + Long.BYTES);
        for (; ; ) {
            try {
                ds.receive(recivedPack);
                //System.out.println("recieved connect parent ack");
                parentActivityTime = System.currentTimeMillis();
                if (rand.nextInt(100) >= loss) {
                    if (recivedPack.getAddress().equals(parent.getInetAddress()) && recivedPack.getPort() == parent.getPort()) {
                        Message ackMessage = new Message(recivedPack);
                        if (ackMessage.getType() == Message.TYPES.ACK) {
                            ByteBuffer buffer = ByteBuffer.allocate(ackMessage.getData().length); // 4 bytes == sizeof (InetAddress)
                            if (ackMessage.getData().length != Long.BYTES) {
                                buffer.put(ackMessage.getData());
                                buffer.flip();
                                Integer newPort = buffer.getInt();
                                //System.out.println(newPort);
                                Integer tmp = buffer.getInt();
                                byte[] newInetAddressParentByte = new byte[tmp];
                                buffer.get(newInetAddressParentByte, 0, tmp);
                                InetAddress newInAd = InetAddress.getByAddress(newInetAddressParentByte);
                                secondParent = new Vertex(newInAd, newPort);
                                //System.out.println("connected to parent with secondparent port:" + newPort);
                            }
                            removeGuidFromMap(buffer);
                            //System.out.println("connected to parent");
                            break;
                        }
                    }
                } else {
                    System.out.println("lost");
                }
            } catch (SocketTimeoutException e) {
                resendMessages();
            }
        }
    }

    private void removeGuidFromMap(ByteBuffer buffer) {
        removeGuidFromMap(buffer.getLong());
    }

    private void removeGuidFromMap(Long guid) {
        for (Iterator<Map.Entry<Message, Long>> it = waitAnswer.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Message, Long> entry = it.next();
            if (entry.getKey().getGUID().equals(guid)) {
                it.remove();
                break;
            }
        }
    }

    private void sendAnswer(Message message) {
        /* Отправляем ответ на сообщение, при условии что это не отввет на наше сообщение
         * */
        ByteBuffer buffer;
        if (message.getType() == Message.TYPES.ACK) {
            return;
        } else if (message.getType() == Message.TYPES.CONNECT) {
            if (parent != null || children.entrySet().size() > 1) {
                if (parent != null) {
                    heir = parent;
                } else { //кладем своего первого ребенка, если его нет, надо передать null
                    Map.Entry<Vertex, Long> entry = children.entrySet().iterator().next();
                    heir = entry.getKey();
                }
                buffer = ByteBuffer.allocate(heir.getInetAddress().getAddress().length + Integer.BYTES * 2 + Long.BYTES);
                buffer.putInt(heir.getPort());
                buffer.putInt(heir.getInetAddress().getAddress().length);
                buffer.put(heir.getInetAddress().getAddress());
                System.out.println("Child possible parent is port:" + heir.getPort());
            }
             else {
                buffer = ByteBuffer.allocate(Long.BYTES);
            }
        } else {
            buffer = ByteBuffer.allocate(Long.BYTES);
        }
        buffer.putLong(message.getGUID());
        buffer.flip();
        //System.out.println(byteBuffer.array().length);
        Message messageAnswer = new Message(Message.TYPES.ACK, buffer.array(), message.getAddress());
        sendMessage(messageAnswer);
        //System.out.println(messageAnswer.getGUID());
    }

    private void receiveMessage(Message message) throws IOException {
        if (message.getAddress().equals(parent)) {
            parentActivityTime = System.currentTimeMillis();
        }
        if (allMessages.contains(message)) {
            return;
        }
        if (message.getType() != Message.TYPES.ACK) {
            if (allMessages.size() == AMOUNT_OF_MESSAGES) {
                allMessages.remove(0);
            }
            allMessages.add(message);
        }

        long lastTime = System.currentTimeMillis();
        switch (message.getType()) {
            case Message.TYPES.CONNECT:
                children.put(message.getAddress(), lastTime);
                break;
            case Message.TYPES.ACK:
                receiveACK(message);
                break;
            case Message.TYPES.MSG:
                System.out.println(new String(message.getData(), Charset.forName("UTF-8")));
                broadcast(message);
                break;
            case Message.TYPES.DEAD:
                System.out.println(new String(message.getData(), Charset.forName("UTF-8")));
                broadcast(new Message(Message.TYPES.MSG, message.getData()));
                break;
            case Message.TYPES.SECOND_PARENT:
                secondParent = null;
                ByteBuffer buffer = ByteBuffer.allocate(message.getData().length); // 4 bytes == sizeof (InetAddress)
                buffer.put(message.getData());
                buffer.flip();
                Integer newPort = buffer.getInt();
                Integer tmp = buffer.getInt();
                byte[] newInetAddressParentByte = new byte[tmp];
                buffer.get(newInetAddressParentByte, 0, tmp);
                byte[] text = new byte[message.getData().length - 2 * Integer.BYTES - tmp];
                buffer.get(text);
                System.out.println(new String(text, Charset.forName("UTF-8")));
                broadcast(new Message(Message.TYPES.MSG, text));
                InetAddress newInAd = InetAddress.getByAddress(newInetAddressParentByte);
                if (!newInAd.equals(InetAddress.getLoopbackAddress()) || !newPort.equals(port)) {
                    secondParent = new Vertex(newInAd, newPort);
                } else {
                    secondParent = null;
                }
                break;
            case Message.TYPES.PING:
                if (!message.getAddress().equals(parent)) {
                    children.put(message.getAddress(), lastTime);
                } else {
                    parentActivityTime = lastTime;
                }
                break;
        }
    }

    private void broadcast(Message message) {
        if (parent != null) {
            if (!parent.equals(message.getAddress())) {
                sendMessage(new Message(message.getType(), message.getData(), parent));
            }
        }

        for (Map.Entry<Vertex, Long> entry : children.entrySet()) {
            Vertex vertex = entry.getKey();
            if (!vertex.equals(message.getAddress())) {
                sendMessage(new Message(message.getType(), message.getData(), vertex));
            }
        }
    }

    private void receiveACK(Message message) {
        ByteBuffer buffer = ByteBuffer.allocate(message.getData().length);
        buffer.put(message.getData());
        buffer.flip();
        removeGuidFromMap(buffer);
    }

    private void resendMessages() {
        for (Message message : waitAnswer.keySet()) {
            sendMessage(message);
        }
    }

    private void sendMessage(Message message) {
        try {
            ds.send(message.getPacket());
            if (message.getType() != Message.TYPES.PING) {
                System.out.println("Sent message with type: " + message.getType() + "AND GUID " + message.getGUID());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (message.getType() != Message.TYPES.ACK) {
            if (waitAnswer.size() < WAIT_MESSAGES) {
                waitAnswer.put(message, System.currentTimeMillis());
            }
        }
    }

    private void checkChildren() {
        for (Iterator<HashMap.Entry<Vertex, Long>> it = children.entrySet().iterator(); it.hasNext(); ) {
            HashMap.Entry<Vertex, Long> var = it.next();
            if (System.currentTimeMillis() - var.getValue() > TIMELIVE) {
                String tmp = var.getKey().getPort() + " has disconnected";
                System.out.println(tmp);
                it.remove();
                if (heir.getInetAddress().equals(var.getKey().getInetAddress()) && heir.getPort().equals(var.getKey().getPort())) { //если наследник
                    if(parent != null){ //TODO нужна ли эта проверка?
                        heir = parent;
                    } else if (children.entrySet().size() > 0) { //кладем своего ребенка первого
                        Map.Entry<Vertex, Long> entry = children.entrySet().iterator().next();
                        heir = entry.getKey();
                    } else {
                        heir = null;
                    }
                    if(!children.isEmpty()){
                        System.out.println("my new heir is" + heir.getPort());
                        createSecondParent(tmp, heir);
                    }
                } else { //сообщаем о смерти
                    ByteBuffer buffer = ByteBuffer.allocate(var.getKey().getInetAddress().getAddress().length + Integer.BYTES * 2 + tmp.getBytes().length);
                    buffer.putInt(var.getKey().getPort());
                    buffer.putInt(var.getKey().getInetAddress().getAddress().length);
                    buffer.put(var.getKey().getInetAddress().getAddress());
                    buffer.put(tmp.getBytes(Charset.forName("UTF-8")));
                    for (Map.Entry<Vertex, Long> mapEntry : children.entrySet()) {
                        sendMessage(new Message(Message.TYPES.DEAD, buffer.array(), mapEntry.getKey()));
                    }
                }
            }
        }
    }
}