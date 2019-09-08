package me.egg82.tfaplus.core;

import java.util.Collection;
import java.util.Objects;

public class StorageData {
    private final Collection<LoginData> loginData;
    private final Collection<AuthyData> authyData;
    private final Collection<TOTPData> totpData;
    private final Collection<HOTPData> hotpData;

    public StorageData(Collection<LoginData> loginData, Collection<AuthyData> authyData, Collection<TOTPData> totpData, Collection<HOTPData> hotpData) {
        if (loginData == null) {
            throw new IllegalArgumentException("loginData cannot be null.");
        }
        if (authyData == null) {
            throw new IllegalArgumentException("authyData cannot be null.");
        }
        if (totpData == null) {
            throw new IllegalArgumentException("totpData cannot be null.");
        }
        if (hotpData == null) {
            throw new IllegalArgumentException("hotpData cannot be null.");
        }

        this.loginData = loginData;
        this.authyData = authyData;
        this.totpData = totpData;
        this.hotpData = hotpData;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StorageData)) return false;
        StorageData that = (StorageData) o;
        return loginData.equals(that.loginData) &&
                authyData.equals(that.authyData) &&
                totpData.equals(that.totpData) &&
                hotpData.equals(that.hotpData);
    }

    public int hashCode() { return Objects.hash(loginData, authyData, totpData, hotpData); }
}
