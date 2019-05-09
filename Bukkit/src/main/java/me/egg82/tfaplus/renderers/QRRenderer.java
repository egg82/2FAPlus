package me.egg82.tfaplus.renderers;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Writer;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import me.egg82.tfaplus.extended.ServiceKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Largely taken from SecureMyAccount
 * https://github.com/games647/SecureMyAccount/blob/master/src/main/java/com/github/games647/securemyaccount/ImageGenerator.java
 */

public class QRRenderer {
    private static final Logger logger = LoggerFactory.getLogger(QRRenderer.class);

    private QRRenderer() {}

    public static BufferedImage getTOTPImage(String playerName, String serverName, String key, String issuer, long codeLength) {
        if (playerName == null) {
            throw new IllegalArgumentException("playerName cannot be null.");
        }
        if (playerName.isEmpty()) {
            throw new IllegalArgumentException("playerName cannot be empty.");
        }
        if (serverName == null) {
            throw new IllegalArgumentException("serverName cannot be null.");
        }
        if (serverName.isEmpty()) {
            throw new IllegalArgumentException("serverName cannot be empty.");
        }
        if (key == null) {
            throw new IllegalArgumentException("key cannot be null.");
        }
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key cannot be empty.");
        }
        if (issuer == null) {
            throw new IllegalArgumentException("issuer cannot be null.");
        }
        if (issuer.isEmpty()) {
            throw new IllegalArgumentException("issuer cannot be empty.");
        }

        String algorithm = ServiceKeys.TOTP_ALGORITM;
        if (algorithm.startsWith("Hmac")) {
            algorithm = algorithm.substring(4);
        }

        try {
            playerName = URLEncoder.encode(playerName, "UTF-8");
            serverName = URLEncoder.encode(serverName, "UTF-8");
            key = URLEncoder.encode(key, "UTF-8");
            issuer = URLEncoder.encode(issuer, "UTF-8");
            algorithm = URLEncoder.encode(algorithm, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            logger.error(ex.getMessage(), ex);
            return null;
        }

        Writer writer = new QRCodeWriter();

        String url = "otpauth://totp/" + issuer + ":" + playerName + "@" + serverName + "?secret=" + key + "&issuer=" + issuer + "&algorithm=" + algorithm + "&digits=" + codeLength + "&period=30";
        try {
            BitMatrix encoded = writer.encode(url, BarcodeFormat.QR_CODE, 128, 128);
            return MatrixToImageWriter.toBufferedImage(encoded);
        } catch (WriterException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return null;
    }

    public static BufferedImage getHOTPImage(String playerName, String serverName, String key, String issuer, long codeLength, long counter) {
        if (playerName == null) {
            throw new IllegalArgumentException("playerName cannot be null.");
        }
        if (playerName.isEmpty()) {
            throw new IllegalArgumentException("playerName cannot be empty.");
        }
        if (serverName == null) {
            throw new IllegalArgumentException("serverName cannot be null.");
        }
        if (serverName.isEmpty()) {
            throw new IllegalArgumentException("serverName cannot be empty.");
        }
        if (key == null) {
            throw new IllegalArgumentException("key cannot be null.");
        }
        if (key.isEmpty()) {
            throw new IllegalArgumentException("key cannot be empty.");
        }
        if (issuer == null) {
            throw new IllegalArgumentException("issuer cannot be null.");
        }
        if (issuer.isEmpty()) {
            throw new IllegalArgumentException("issuer cannot be empty.");
        }

        String algorithm = ServiceKeys.HOTP_ALGORITM;
        if (algorithm.startsWith("Hmac")) {
            algorithm = algorithm.substring(4);
        }

        try {
            playerName = URLEncoder.encode(playerName, "UTF-8");
            serverName = URLEncoder.encode(serverName, "UTF-8");
            key = URLEncoder.encode(key, "UTF-8");
            issuer = URLEncoder.encode(issuer, "UTF-8");
            algorithm = URLEncoder.encode(algorithm, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            logger.error(ex.getMessage(), ex);
            return null;
        }

        Writer writer = new QRCodeWriter();

        String url = "otpauth://hotp/" + issuer + ":" + playerName + "@" + serverName + "?secret=" + key + "&issuer=" + issuer + "&algorithm=" + algorithm + "&digits=" + codeLength + "&counter=" + counter;
        try {
            BitMatrix encoded = writer.encode(url, BarcodeFormat.QR_CODE, 128, 128);
            return MatrixToImageWriter.toBufferedImage(encoded);
        } catch (WriterException ex) {
            logger.error(ex.getMessage(), ex);
        }

        return null;
    }
}
