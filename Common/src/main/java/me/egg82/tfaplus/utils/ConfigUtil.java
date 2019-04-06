package me.egg82.tfaplus.utils;

import me.egg82.tfaplus.TFAAPI;
import me.egg82.tfaplus.extended.CachedConfigValues;
import me.egg82.tfaplus.extended.Configuration;
import ninja.egg82.service.ServiceLocator;
import ninja.egg82.service.ServiceNotFoundException;

import javax.annotation.Nullable;

public class ConfigUtil {
    /**
     * Grabs the config instance from ServiceLocator
     * @return instance of the Configuration class, may return null
     */
    public static @Nullable Configuration getConfig()
    {
        Configuration config;

        try {
            config = ServiceLocator.get(Configuration.class);
            return config;
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            TFAAPI.getLogger().error(ex.getMessage(), ex);
        }

        return null;
    }

    /**
     * Grabs the cached config instance from ServiceLocator
     * @return instance of the CachedConfigValues class, may return null
     */
    public static @Nullable CachedConfigValues getCachedConfig()
    {
        CachedConfigValues cachedConfig;

        try {
            cachedConfig = ServiceLocator.get(CachedConfigValues.class);
            return cachedConfig;
        } catch (IllegalAccessException | InstantiationException | ServiceNotFoundException ex) {
            TFAAPI.getLogger().error(ex.getMessage(), ex);
        }

        return null;
    }
}
