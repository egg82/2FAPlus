package me.egg82.tfaplus.enums;

import co.aikar.locales.MessageKey;
import co.aikar.locales.MessageKeyProvider;

public enum Message implements MessageKeyProvider {
    DESCRIPTION__MAIN_HELP,

    GENERAL__HEADER,
    GENERAL__ENABLED,
    GENERAL__DISABLED,
    GENERAL__LOAD,
    GENERAL__HOOK_ENABLE,
    GENERAL__HOOK_DISABLE,
    GENERAL__UPDATE,

    ERROR__INTERNAL,
    ERROR__NO_UUID,
    ERROR__PLAYER_ONLY,
    ERROR__NEED_ADMIN_OTHER,

    RELOAD__BEGIN,
    RELOAD__END,

    REGISTER__BEGIN,
    REGISTER__SUCCESS,
    REGISTER__FAILURE,
    REGISTER__KEY,
    REGISTER__KEY_OTHER,
    REGISTER__QR_CODE,
    REGISTER__WARNING_PRIVACY,

    DELETE__BEGIN,
    DELETE__SUCCESS,
    DELETE__FAILURE,

    CHECK__BEGIN,
    CHECK__YES,
    CHECK__NO,

    SEEK__2FA_NOT_ENABLED,
    SEEK__NEXT_CODES;

    private final MessageKey key = MessageKey.of(name().toLowerCase().replace("__", "."));
    public MessageKey getMessageKey() { return key; }
}
