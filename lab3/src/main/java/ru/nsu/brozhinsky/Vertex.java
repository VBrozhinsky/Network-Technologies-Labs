package ru.nsu.brozhinsky;

import java.net.InetAddress;
import java.util.Objects;

public class Vertex {
    private InetAddress inetAddress;
    private Integer port;

    public Vertex(InetAddress inetAddress, Integer port) {
        this.inetAddress = inetAddress;
        this.port = port;
    }

    public InetAddress getInetAddress() {
        return inetAddress;
    }

    public Integer getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vertex vertex = (Vertex) o;
        return Objects.equals(inetAddress, vertex.inetAddress) &&
                Objects.equals(port, vertex.port);
    }

    @Override
    public int hashCode() {
        return Objects.hash(inetAddress, port);
    }
}
