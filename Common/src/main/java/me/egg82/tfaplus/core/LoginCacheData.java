package me.egg82.tfaplus.core;

import java.util.UUID;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class LoginCacheData {
    private final UUID uuid;
    private final String ip;

    private final int hashCode;

    public LoginCacheData(UUID uuid, String ip) {
        this.uuid = uuid;
        this.ip = ip;

        hashCode = new HashCodeBuilder(17, 37)
                .append(uuid)
                .append(ip)
                .toHashCode();
    }

    public UUID getUUID() { return uuid; }

    public String getIP() { return ip; }

    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        LoginCacheData that = (LoginCacheData) o;

        return new EqualsBuilder()
                .append(uuid, that.uuid)
                .append(ip, that.ip)
                .isEquals();
    }

    public int hashCode() {
        return hashCode;
    }
}
