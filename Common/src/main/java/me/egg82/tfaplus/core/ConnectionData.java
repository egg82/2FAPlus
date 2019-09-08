package me.egg82.tfaplus.core;

import java.util.Objects;

public class ConnectionData {
    private final String host;
    private final int port;

    private final int hashCode;

    public ConnectionData(String host, int port) {
        if (host == null) {
            throw new IllegalArgumentException("host cannot be null.");
        }
        host = host.trim();
        if (host.isEmpty()) {
            throw new IllegalArgumentException("host cannot be empty.");
        }
        if (port <= 0) {
            throw new IllegalArgumentException("port cannot be <= 0.");
        }

        this.host = host;
        this.port = port;

        this.hashCode = Objects.hash(host, port);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConnectionData)) return false;
        ConnectionData that = (ConnectionData) o;
        return port == that.port &&
                host.equals(that.host);
    }

    public int hashCode() { return hashCode; }
}
