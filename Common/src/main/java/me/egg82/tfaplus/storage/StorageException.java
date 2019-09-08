package me.egg82.tfaplus.storage;

public class StorageException extends Exception {
    public StorageException(String message) { super(message); }

    public StorageException(Throwable cause) { super(cause); }

    public StorageException(String message, Throwable cause) { super(message, cause); }
}
