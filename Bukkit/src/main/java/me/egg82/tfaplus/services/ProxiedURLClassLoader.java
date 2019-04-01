package me.egg82.tfaplus.services;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class ProxiedURLClassLoader extends URLClassLoader {
    private static final Method FIND_METHOD;

    static {
        try {
            FIND_METHOD = URLClassLoader.class.getDeclaredMethod("findClass", String.class);
            FIND_METHOD.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }

        ClassLoader.registerAsParallelCapable();
    }

    private ClassLoader parent;
    private ClassLoader system;
    private boolean parentIsSystem;

    public ProxiedURLClassLoader(ClassLoader parent) {
        super(new URL[0]);
        this.parent = parent;
        system = getSystemClassLoader();
        parentIsSystem = parent == system;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> clazz = null;

        // Find in system first (JVM, classpath, etc)
        if (system != null && !parentIsSystem) {
            try {
                clazz = (Class<?>) FIND_METHOD.invoke(system, name);
            } catch (IllegalAccessException | InvocationTargetException ignored) {}
        }

        if (clazz == null) {
            // Not found in system (or parent is system)
            try {
                // Find in local
                clazz = super.findClass(name);
            } catch (ClassNotFoundException ignored) {
                // Find in parent
                try {
                    clazz = (Class<?>) FIND_METHOD.invoke(parent, name);
                } catch (InvocationTargetException ex) {
                    if (ex.getCause() instanceof ClassNotFoundException) {
                        throw (ClassNotFoundException) ex.getCause();
                    }
                } catch (IllegalAccessException ignored2) {}
            }
        }

        // We are guaranteed a result here, as the "find in parent" would have thrown an exception otherwise
        return clazz;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // Check if class has been loaded
        Class<?> clazz = findLoadedClass(name);

        // Load system classes first (JVM, classpath, etc)
        if (clazz == null && system != null && !parentIsSystem) {
            try {
                clazz = system.loadClass(name);
            } catch (ClassNotFoundException ignored) {}
        }

        if (clazz == null) {
            // Not found in system (or parent is system)
            try {
                // Find in local
                clazz = findClass(name);
            } catch (ClassNotFoundException ignored) {
                // Find in parent
                try {
                    clazz = (Class<?>) FIND_METHOD.invoke(parent, name);
                } catch (InvocationTargetException ex) {
                    if (ex.getCause() instanceof ClassNotFoundException) {
                        throw (ClassNotFoundException) ex.getCause();
                    }
                } catch (IllegalAccessException ignored2) {}
            }
        }

        // We are guaranteed a result here, as the "find in parent" would have thrown an exception otherwise
        if (resolve) {
            resolveClass(clazz);
        }
        return clazz;
    }

    @Override
    public URL getResource(String name) {
        URL url = null;

        // Find in system first (JVM, classpath, etc)
        if (system != null && !parentIsSystem) {
            url = system.getResource(name);
        }

        // Find in local
        if (url == null) {
            url = findResource(name);
        }

        // Find in parent
        if (url == null) {
            url = parent.getResource(name);
        }

        return url;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        List<URL> urls = new ArrayList<>();

        Enumeration<URL> enums = null;

        // Load system first (JVM, classpath, etc)
        if (system != null && !parentIsSystem) {
            enums = system.getResources(name);
        }
        if (enums != null) {
            while (enums.hasMoreElements()) {
                urls.add(enums.nextElement());
            }
        }

        // Load local
        enums = findResources(name);
        if (enums != null) {
            while (enums.hasMoreElements()) {
                urls.add(enums.nextElement());
            }
        }

        // Load parent
        if (parent != null) {
            enums = parent.getResources(name);
            if (enums != null) {
                while (enums.hasMoreElements()) {
                    urls.add(enums.nextElement());
                }
            }
        }

        return new Enumeration<URL>() {
            Iterator<URL> i = urls.iterator();
            public boolean hasMoreElements() {
                return i.hasNext();
            }
            public URL nextElement() {
                return i.next();
            }
        };
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        URL url = getResource(name);
        try {
            return url != null ? url.openStream() : null;
        } catch (IOException ignored) {}
        return null;
    }
}
