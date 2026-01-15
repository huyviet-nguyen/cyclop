package com.tbot.cyclop.orderplacer.util;

import org.apache.commons.codec.binary.Hex;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class GenericHttpUtil {

    public static final String ENCRYPT_SECRET_KEY = "[ENCRYPT_SECRET_KEY]";
    private static final String ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";

    public static final String PUBLIC_KEY = "[RSA_PUBLIC_KEY]";

    public static String calculateHmacSHA256(String secret, String message) {
        Mac hmacSha256;
        try {
            hmacSha256 = Mac.getInstance("HmacSHA256");
            SecretKeySpec secKey =
                    new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmacSha256.init(secKey);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("No such algorithm: " + e.getMessage());
        } catch (InvalidKeyException e) {
            throw new RuntimeException("Invalid key: " + e.getMessage());
        }
        byte[] hash = hmacSha256.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return Hex.encodeHexString(hash);
    }

    public static String decryptSecretKey(String encryptedText) {
        try {
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);
            byte[] salt = new byte[8];
            System.arraycopy(encryptedBytes, 8, salt, 0, 8);

            final byte[][] keyAndIV = generateKeyAndIV(32, 16, 1, salt, ENCRYPT_SECRET_KEY.getBytes(StandardCharsets.UTF_8),
                    MessageDigest.getInstance("MD5"));

            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyAndIV[0], "AES"), new IvParameterSpec(keyAndIV[1]));
            byte[] decryptedData = cipher.doFinal(encryptedBytes, 16, encryptedBytes.length - 16);
            return new String(decryptedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static byte[][] generateKeyAndIV(int keyLength, int ivLength, int iterations, byte[] salt, byte[] password, MessageDigest md) {
        int digestLength = md.getDigestLength();
        int requiredLength = (keyLength + ivLength + digestLength - 1) / digestLength * digestLength;
        byte[] generatedData = new byte[requiredLength];
        int generatedLength = 0;
        try {
            md.reset();
            while (generatedLength < keyLength + ivLength) {
                if (generatedLength > 0)
                    md.update(generatedData, generatedLength - digestLength, digestLength);
                md.update(password);
                if (salt != null)
                    md.update(salt, 0, 8);
                md.digest(generatedData, generatedLength, digestLength);
                generatedLength += digestLength;
            }
            byte[][] keyAndIV = new byte[2][];
            keyAndIV[0] = new byte[keyLength];
            keyAndIV[1] = new byte[ivLength];
            System.arraycopy(generatedData, 0, keyAndIV[0], 0, keyLength);
            System.arraycopy(generatedData, keyLength, keyAndIV[1], 0, ivLength);
            return keyAndIV;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String buildQueryString(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "";

        List<String> keyValuePairs = new ArrayList<>();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            keyValuePairs.add(stringifyKeyValuePair(key, value));
        }

        return String.join("&", keyValuePairs);
    }

    private static String stringifyKeyValuePair(String key, Object value) {
        String valueString;
        if (value instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<String> valueList = (List<String>) value;
            valueString = "[" + String.join(",", valueList) + "]";
        } else {
            valueString = value.toString();
        }
        return key + "=" + URLEncoder.encode(valueString, StandardCharsets.UTF_8);
    }

    public static String exceptionToString(Exception e) {
        StringWriter string_writer = new StringWriter();
        e.printStackTrace(
                new PrintWriter(string_writer));
        return string_writer.toString();
    }
}
