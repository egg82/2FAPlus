package me.egg82.tfaplus.enums;

import co.aikar.locales.MessageKey;
import co.aikar.locales.MessageKeyProvider;

public enum Message implements MessageKeyProvider {
    DESCRIPTION__MAIN_HELP,

    GENERAL__HEADER,

    ERROR__INTERNAL,
    ERROR__NO_UUID,
    ERROR__PLAYER_ONLY,
    ERROR__NEED_ADMIN_OTHER,

    RELOAD__BEGIN,
    RELOAD__END,

    REGISTER__BEGIN,
    REGISTER__SUCCESS,
    REGISTER__FAILURE,

    SEEK__2FA_NOT_ENABLED,
    SEEK__NEXT_CODES;

    private final MessageKey key = MessageKey.of(name().toLowerCase().replace("__", "."));
    public MessageKey getMessageKey() { return key; }
}
