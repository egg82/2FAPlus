package me.egg82.tfaplus.messaging;

public class MessagingException extends Exception {
    public MessagingException(String message) { super(message); }

    public MessagingException(Throwable cause) { super(cause); }

    public MessagingException(String message, Throwable cause) { super(message, cause); }
}
