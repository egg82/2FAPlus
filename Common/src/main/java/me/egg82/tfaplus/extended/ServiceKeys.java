package me.egg82.tfaplus.extended;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;

public class ServiceKeys {
    private ServiceKeys() {}

    public static final String GAMEANALYTICS_KEY = "b845fd7be1001a18d19002891f732863";
    public static final String GAMEANALYTICS_SECRET = "922cdf36dbd07eb0180fe9c882a6d69195bed9a6";

    public static final String TOTP_ALGORITM = TimeBasedOneTimePasswordGenerator.TOTP_ALGORITHM_HMAC_SHA256;
}
