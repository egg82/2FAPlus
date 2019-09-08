package me.egg82.tfaplus.extended;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import me.egg82.tfaplus.auth.AuthenticationHandler;
import me.egg82.tfaplus.auth.data.AuthenticationData;
import me.egg82.tfaplus.core.FreezeConfigContainer;
import me.egg82.tfaplus.enums.AuthenticationType;
import me.egg82.tfaplus.services.InternalAPI;

public class CachedConfigValues {
    private CachedConfigValues() {}

    private boolean debug = false;
    public boolean getDebug() { return debug; }

    private long ipCacheTime = TimeUnit.DAYS.toMillis(30L);
    public long getIPCacheTime() { return ipCacheTime; }

    private long verificationCacheTime = TimeUnit.MINUTES.toMillis(3L);
    public long getVerificationCacheTime() { return verificationCacheTime; }

    private ImmutableSet<String> commands = ImmutableSet.of();
    public ImmutableSet<String> getCommands() { return commands; }

    private boolean forceAuth = true;
    public boolean getForceAuth() { return forceAuth; }

    private long maxAttempts = 3L;
    public long getMaxAttempts() { return maxAttempts; }

    private FreezeConfigContainer freeze = null;
    public FreezeConfigContainer getFreeze() { return freeze; }

    private ImmutableSet<String> ignored = ImmutableSet.of();
    public ImmutableSet<String> getIgnored() { return ignored; }

    private ImmutableMap<AuthenticationType, AuthenticationHandler<?>> authentication = ImmutableMap.of();
    public boolean hasAuthenticationHandler(AuthenticationType type) { return authentication.containsKey(type); }
    public <T extends AuthenticationData> AuthenticationHandler<T> getAuthenticationHandler(AuthenticationType type) { return (AuthenticationHandler<T>) authentication.get(type); }
    public ImmutableMap<AuthenticationType, AuthenticationHandler<?>> getAuthentication() { return authentication; }

    private String serverName = null;
    public String getServerName() { return serverName; }

    public static CachedConfigValues.Builder builder() { return new CachedConfigValues.Builder(); }

    public static class Builder {
        private final CachedConfigValues values = new CachedConfigValues();

        private Builder() {}

        public CachedConfigValues.Builder debug(boolean value) {
            values.debug = value;
            return this;
        }

        public CachedConfigValues.Builder ipCacheTime(long value, TimeUnit unit) {
            if (value <= 0L) {
                throw new IllegalArgumentException("value cannot be <= 0.");
            }
            if (unit == null) {
                throw new IllegalArgumentException("unit cannot be null.");
            }

            values.ipCacheTime = unit.toMillis(value);
            return this;
        }

        public CachedConfigValues.Builder verificationCacheTime(long value, TimeUnit unit) {
            if (value <= 0L) {
                throw new IllegalArgumentException("value cannot be <= 0.");
            }
            if (unit == null) {
                throw new IllegalArgumentException("unit cannot be null.");
            }

            values.verificationCacheTime = unit.toMillis(value);
            return this;
        }

        public CachedConfigValues.Builder commands(Collection<String> value) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null.");
            }

            values.commands = ImmutableSet.copyOf(value);
            return this;
        }

        public CachedConfigValues.Builder forceAuth(boolean value) {
            values.forceAuth = value;
            return this;
        }

        public CachedConfigValues.Builder maxAttempts(long value) {
            values.maxAttempts = value;
            return this;
        }

        public CachedConfigValues.Builder freeze(FreezeConfigContainer value) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null.");
            }

            values.freeze = value;
            return this;
        }

        public CachedConfigValues.Builder ignored(Collection<String> value) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null.");
            }

            values.ignored = ImmutableSet.copyOf(value);
            return this;
        }

        public CachedConfigValues.Builder authentication(ImmutableMap<AuthenticationType, AuthenticationHandler<?>> value) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null.");
            }

            values.authentication = value;
            return this;
        }

        public CachedConfigValues.Builder serverName(String value) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null.");
            }

            values.serverName = value;
            return this;
        }

        public CachedConfigValues build() {
            InternalAPI.changeVerificationTime(values.verificationCacheTime);
            return values;
        }
    }
}
