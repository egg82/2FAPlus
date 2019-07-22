package me.egg82.tfaplus.enums;

import co.aikar.locales.MessageKey;
import co.aikar.locales.MessageKeyProvider;

public enum Message implements MessageKeyProvider {
    GENERAL__HEADER,

    ERROR__INTERNAL,
    ERROR__PLAYER_ONLY,

    SEEK__2FA_NOT_ENABLED,
    SEEK__NEXT_CODES;

    private final MessageKey key = MessageKey.of(name().toLowerCase().replace("__", "."));
    public MessageKey getMessageKey() { return key; }
}
