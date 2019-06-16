package me.egg82.tfaplus.extended;

import com.authy.AuthyApiClient;
import com.google.common.collect.ImmutableSet;
import com.rabbitmq.client.ConnectionFactory;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import me.egg82.tfaplus.core.FreezeConfigContainer;
import me.egg82.tfaplus.enums.SQLType;
import me.egg82.tfaplus.services.InternalAPI;
import ninja.egg82.sql.SQL;
import ninja.egg82.tuples.longs.LongObjectPair;
import redis.clients.jedis.JedisPool;

public class CachedConfigValues {
    private CachedConfigValues() {}

    private boolean debug = false;
    public boolean getDebug() { return debug; }

    private LongObjectPair<TimeUnit> ipCacheTime = new LongObjectPair<>(30L, TimeUnit.DAYS);
    public long getIPCacheTime() { return ipCacheTime.getSecond().toMillis(ipCacheTime.getFirst()); }

    private LongObjectPair<TimeUnit> verificationCacheTime = new LongObjectPair<>(3L, TimeUnit.MINUTES);
    public long getVerificationCacheTime() { return verificationCacheTime.getSecond().toMillis(verificationCacheTime.getFirst()); }

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

    private JedisPool redisPool = null;
    public JedisPool getRedisPool() { return redisPool; }

    private ConnectionFactory rabbitConnectionFactory = null;
    public ConnectionFactory getRabbitConnectionFactory() { return rabbitConnectionFactory; }

    private SQL sql = null;
    public SQL getSQL() { return sql; }

    private SQLType sqlType = SQLType.SQLite;
    public SQLType getSQLType() { return sqlType; }

    private String serverName = null;
    public String getServerName() { return serverName; }

    private AuthyApiClient authy = null;
    public Optional<AuthyApiClient> getAuthy() { return Optional.ofNullable(authy); }

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

            values.ipCacheTime = new LongObjectPair<>(value, unit);
            return this;
        }

        public CachedConfigValues.Builder verificationCacheTime(long value, TimeUnit unit) {
            if (value <= 0L) {
                throw new IllegalArgumentException("value cannot be <= 0.");
            }

            values.verificationCacheTime = new LongObjectPair<>(value, unit);
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

        public CachedConfigValues.Builder redisPool(JedisPool value) {
            values.redisPool = value;
            return this;
        }

        public CachedConfigValues.Builder rabbitConnectionFactory(ConnectionFactory value) {
            values.rabbitConnectionFactory = value;
            return this;
        }

        public CachedConfigValues.Builder sql(SQL value) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null.");
            }
            values.sql = value;
            return this;
        }

        public CachedConfigValues.Builder sqlType(String value) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null.");
            }
            values.sqlType = SQLType.getByName(value);
            return this;
        }

        public CachedConfigValues.Builder serverName(String value) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null.");
            }
            values.serverName = value;
            return this;
        }

        public CachedConfigValues.Builder authy(Optional<AuthyApiClient> value) {
            if (value == null) {
                throw new IllegalArgumentException("value cannot be null.");
            }
            if (!value.isPresent()) {
                return this;
            }

            values.authy = value.get();
            return this;
        }

        public CachedConfigValues build() {
            InternalAPI.changeVerificationTime(values.verificationCacheTime.getFirst(), values.verificationCacheTime.getSecond());
            return values;
        }
    }
}
