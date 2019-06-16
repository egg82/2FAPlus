package me.egg82.tfaplus;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ExternalAPI {
    private static ExternalAPI api = null;

    private final Object concrete;
    private final Class<?> concreteClass;
    private final Class<?> exceptionClass;

    private final ConcurrentMap<String, Method> methodCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Method> exceptionMethodCache = new ConcurrentHashMap<>();

    private ExternalAPI(URLClassLoader classLoader) {
        if (classLoader == null) {
            throw new IllegalArgumentException("classLoader cannot be null.");
        }

        try {
            concreteClass = classLoader.loadClass("me.egg82.tfaplus.TFAAPI");
            Constructor<?> constructor = concreteClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            concrete = constructor.newInstance();
            exceptionClass = classLoader.loadClass("me.egg82.tfaplus.APIException");
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new RuntimeException("Could not get TFAAPI from classLoader.", ex);
        }
    }

    public static ExternalAPI getInstance() { return api; }

    public static void setInstance(URLClassLoader classLoader) {
        if (api != null) {
            throw new IllegalStateException("api is already set.");
        }
        api = new ExternalAPI(classLoader);
    }

    /**
     * Returns the current time in millis according to the SQL database server
     *
     * @return The current time, in millis, from the database server
     * @throws APIException if there was an error while attempting to get the time
     */
    public long getCurrentSQLTime() throws APIException {
        try {
            return (Long) invokeMethod("getCurrentSQLTime");
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new APIException(true, "Could not invoke base method.", ex);
        } catch (InvocationTargetException ex) {
            Throwable t = ex.getTargetException();
            if (t.getClass().getName().equals("me.egg82.tfaplus.APIException")) {
                throw convertToAPIException(t);
            }
            throw new APIException(true, "Could not invoke base method.", ex);
        }
    }

    /**
     * Register a new Authy user from an existing player
     *
     * @param uuid The player UUID
     * @param email The user's e-mail address
     * @param phone The user's phone number
     * @throws APIException if there was an error while attempting to register the player
     */
    public void registerAuthy(UUID uuid, String email, String phone) throws APIException { registerAuthy(uuid, email, phone, "1"); }

    /**
     * Register a new Authy user from an existing player
     *
     * @param uuid The player UUID
     * @param email The user's e-mail address
     * @param phone The user's phone number
     * @param countryCode The user's phone numbers' country code
     * @throws APIException if there was an error while attempting to register the player
     */
    public void registerAuthy(UUID uuid, String email, String phone, String countryCode) throws APIException {
        try {
            invokeMethod("registerAuthy", uuid, email, phone, countryCode);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new APIException(true, "Could not invoke base method.", ex);
        } catch (InvocationTargetException ex) {
            Throwable t = ex.getTargetException();
            if (t.getClass().getName().equals("me.egg82.tfaplus.APIException")) {
                throw convertToAPIException(t);
            }
            throw new APIException(true, "Could not invoke base method.", ex);
        }
    }

    /**
     * Register a new TOTP user from an existing player
     *
     * @param uuid The player UUID
     * @param codeLength The length of the TOTP code (eg. 6 would generate a 6-digit code)
     * @return A base32-encoded private key
     * @throws APIException if there was an error while attempting to register the player
     */
    public String registerTOTP(UUID uuid, long codeLength) throws APIException {
        try {
            return (String) invokeMethod("registerTOTP", uuid, codeLength);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new APIException(true, "Could not invoke base method.", ex);
        } catch (InvocationTargetException ex) {
            Throwable t = ex.getTargetException();
            if (t.getClass().getName().equals("me.egg82.tfaplus.APIException")) {
                throw convertToAPIException(t);
            }
            throw new APIException(true, "Could not invoke base method.", ex);
        }
    }

    /**
     * Register a new HOTP user from an existing player
     *
     * @param uuid The player UUID
     * @param codeLength The length of the HOTP code (eg. 6 would generate a 6-digit code)
     * @return A base32-encoded private key
     * @throws APIException if there was an error while attempting to register the player
     */
    public String registerHOTP(UUID uuid, long codeLength) throws APIException { return registerHOTP(uuid, codeLength, 0L); }

    /**
     * Register a new HOTP user from an existing player
     *
     * @param uuid The player UUID
     * @param codeLength The length of the HOTP code (eg. 6 would generate a 6-digit code)
     * @param initialCounterValue The initial value of the HOTP counter
     * @return A base32-encoded private key
     * @throws APIException if there was an error while attempting to register the player
     */
    public String registerHOTP(UUID uuid, long codeLength, long initialCounterValue) throws APIException {
        try {
            return (String) invokeMethod("registerHOTP", uuid, codeLength, initialCounterValue);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new APIException(true, "Could not invoke base method.", ex);
        } catch (InvocationTargetException ex) {
            Throwable t = ex.getTargetException();
            if (t.getClass().getName().equals("me.egg82.tfaplus.APIException")) {
                throw convertToAPIException(t);
            }
            throw new APIException(true, "Could not invoke base method.", ex);
        }
    }

    /**
     * Re-synchronizes the server HOTP counter with the user's HOTP counter
     * using the tokens provided. The tokens will be a sequence provided from the client
     * that the server will then "seek" to in order to re-set the counter.
     *
     * eg. The user will generate the next X number of tokens from their client (in order) and
     * the server will then re-set its counter to match that of the client's using that token sequence.
     *
     * @param uuid The player UUID
     * @param tokens The token sequence provided by the user
     * @throws APIException if there was an error while attempting to seek the player's HOTP counter
     */
    public void seekHOTPCounter(UUID uuid, Collection<String> tokens) throws APIException {
        if (tokens == null) {
            throw new IllegalArgumentException("tokens cannot be null.");
        }

        seekHOTPCounter(uuid, tokens.toArray(new String[0]));
    }

    /**
     * Re-synchronizes the server HOTP counter with the user's HOTP counter
     * using the tokens provided. The tokens will be a sequence provided from the client
     * that the server will then "seek" to in order to re-set the counter.
     *
     * eg. The user will generate the next X number of tokens from their client (in order) and
     * the server will then re-set its counter to match that of the client's using that token sequence.
     *
     * @param uuid The player UUID
     * @param tokens The token sequence provided by the user
     * @throws APIException if there was an error while attempting to seek the player's HOTP counter
     */
    public void seekHOTPCounter(UUID uuid, String[] tokens) throws APIException {
        try {
            invokeMethod("seekHOTPCounter", uuid, tokens);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new APIException(true, "Could not invoke base method.", ex);
        } catch (InvocationTargetException ex) {
            Throwable t = ex.getTargetException();
            if (t.getClass().getName().equals("me.egg82.tfaplus.APIException")) {
                throw convertToAPIException(t);
            }
            throw new APIException(true, "Could not invoke base method.", ex);
        }
    }

    /**
     * Returns the current registration status of a player
     *
     * @param uuid The player UUID
     * @return Whether or not the player is currently registered
     * @throws APIException if there was an error while attempting to get prerequisites
     */
    public boolean isRegistered(UUID uuid) throws APIException {
        try {
            return (Boolean) invokeMethod("isRegistered", uuid);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new APIException(true, "Could not invoke base method.", ex);
        } catch (InvocationTargetException ex) {
            Throwable t = ex.getTargetException();
            if (t.getClass().getName().equals("me.egg82.tfaplus.APIException")) {
                throw convertToAPIException(t);
            }
            throw new APIException(true, "Could not invoke base method.", ex);
        }
    }

    /**
     * Deletes an existing user from a player
     *
     * @param uuid The player UUID
     * @throws APIException if there was an error while attempting to delete the player
     */
    public void delete(UUID uuid) throws APIException {
        try {
            invokeMethod("delete", uuid);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new APIException(true, "Could not invoke base method.", ex);
        } catch (InvocationTargetException ex) {
            Throwable t = ex.getTargetException();
            if (t.getClass().getName().equals("me.egg82.tfaplus.APIException")) {
                throw convertToAPIException(t);
            }
            throw new APIException(true, "Could not invoke base method.", ex);
        }
    }

    /**
     * Returns the current verification status of a player
     *
     * @param uuid The player UUID
     * @param refresh Whether or not to reset the verification timer
     * @return Whether or not the player is currently verified through the verification timeout
     * @throws APIException if there was an error while attempting to get prerequisites
     */
    public boolean isVerified(UUID uuid, boolean refresh) throws APIException {
        try {
            return (Boolean) invokeMethod("isVerified", uuid, refresh);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new APIException(true, "Could not invoke base method.", ex);
        } catch (InvocationTargetException ex) {
            Throwable t = ex.getTargetException();
            if (t.getClass().getName().equals("me.egg82.tfaplus.APIException")) {
                throw convertToAPIException(t);
            }
            throw new APIException(true, "Could not invoke base method.", ex);
        }
    }

    /**
     * Forces a verification check for the player
     *
     * @param uuid The player UUID
     * @param token 2FA token to verify against
     * @return A boolean value. True = success, false = failure
     * @throws APIException if there was an error while attempting to verify the player
     */
    public boolean verify(UUID uuid, String token) throws APIException {
        try {
            return (Boolean) invokeMethod("verify", uuid, token);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new APIException(true, "Could not invoke base method.", ex);
        } catch (InvocationTargetException ex) {
            Throwable t = ex.getTargetException();
            if (t.getClass().getName().equals("me.egg82.tfaplus.APIException")) {
                throw convertToAPIException(t);
            }
            throw new APIException(true, "Could not invoke base method.", ex);
        }
    }

    private Object invokeMethod(String name, Object... params) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method tmp = methodCache.get(name);
        if (tmp == null) {
            synchronized (this) {
                tmp = methodCache.get(name);
                if (tmp == null) {
                    tmp = concreteClass.getMethod(name, getParamClasses(params));
                    methodCache.put(name, tmp);
                }
            }
        }

        return tmp.invoke(concrete, params);
    }

    private Object invokeExceptionMethod(String name, Throwable ex, Object... params) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        Method tmp = exceptionMethodCache.get(name);
        if (tmp == null) {
            synchronized (this) {
                tmp = exceptionMethodCache.get(name);
                if (tmp == null) {
                    tmp = exceptionClass.getMethod(name, getParamClasses(params));
                    exceptionMethodCache.put(name, tmp);
                }
            }
        }

        return tmp.invoke(ex, params);
    }

    private Class[] getParamClasses(Object[] params) {
        Class[] retVal = new Class[params.length];
        for (int i = 0; i < params.length; i++) {
            retVal[i] = (params[i] != null) ? params[i].getClass() : null;
        }
        return retVal;
    }

    private APIException convertToAPIException(Throwable e) throws APIException {
        try {
            boolean hard = (Boolean) invokeExceptionMethod("isHard", e);
            String message = (String) invokeExceptionMethod("getMessage", e);
            Throwable cause = (Throwable) invokeExceptionMethod("getCause", e);
            return new APIException(hard, message, cause);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
            throw new APIException(true, "Could not convert exception.", ex);
        }
    }
}
