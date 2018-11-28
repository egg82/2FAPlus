package me.egg82.tfaplus.core;

public class SQLFetchResult {
    private final LoginData[] loginData;
    private final AuthyData[] authyData;
    private final TOTPData[] totpData;
    private final HOTPData[] hotpData;
    private final String[] removedKeys;

    public SQLFetchResult(LoginData[] loginData, AuthyData[] authyData, TOTPData[] totpData, HOTPData hotpData[], String[] removedKeys) {
        this.loginData = loginData;
        this.authyData = authyData;
        this.totpData = totpData;
        this.hotpData = hotpData;
        this.removedKeys = removedKeys;
    }

    public LoginData[] getLoginData() { return loginData; }

    public AuthyData[] getAuthyData() { return authyData; }

    public TOTPData[] getTOTPData() { return totpData; }

    public HOTPData[] getHOTPData() { return hotpData; }

    public String[] getRemovedKeys() { return removedKeys; }
}
