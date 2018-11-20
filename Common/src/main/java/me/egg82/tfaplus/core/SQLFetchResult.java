package me.egg82.tfaplus.core;

public class SQLFetchResult {
    private final LoginData[] loginData;
    private final AuthyData[] authyData;
    private final String[] removedKeys;

    public SQLFetchResult(LoginData[] loginData, AuthyData[] authyData, String[] removedKeys) {
        this.loginData = loginData;
        this.authyData = authyData;
        this.removedKeys = removedKeys;
    }

    public LoginData[] getLoginData() { return loginData; }

    public AuthyData[] getAuthyData() { return authyData; }

    public String[] getRemovedKeys() { return removedKeys; }
}
