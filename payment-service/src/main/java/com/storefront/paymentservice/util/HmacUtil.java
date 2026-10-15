package com.storefront.paymentservice.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HmacUtil {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private HmacUtil() {}

    public static String calculateHmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA256 signature", e);
        }
    }

    public static boolean verifyHmac(String data, String secret, String expectedSignature) {
        if (data == null || secret == null || expectedSignature == null) {
            return false;
        }
        String computed = calculateHmac(data, secret);
        return MessageDigest.isEqual(computed.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8));
    }
}
