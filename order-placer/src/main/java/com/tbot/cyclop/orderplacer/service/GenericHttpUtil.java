package com.tbot.cyclop.orderplacer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Component
public class GenericHttpUtil {

    public static final String ENCRYPT_SECRET_KEY = "sec1r2e3t-vv";
    private static final String ENCRYPTION_ALGORITHM = "AES/CBC/PKCS5Padding";
    public static String calculateHmacSHA256(String secret, String message)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac hmacSha256 = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        hmacSha256.init(secretKey);
        return new String(hmacSha256.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    public static String encryptSecretKey(String secretKey) {
        try {
            SecretKey key = new SecretKeySpec(ENCRYPT_SECRET_KEY.getBytes(StandardCharsets.UTF_8), ENCRYPTION_ALGORITHM);
            Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encryptedBytes = cipher.doFinal(secretKey.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
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
}
