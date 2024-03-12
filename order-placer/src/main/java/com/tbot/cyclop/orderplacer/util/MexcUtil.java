//package com.tbot.cyclop.orderplacer.util;
//
//import javax.crypto.Cipher;
//import javax.crypto.spec.GCMParameterSpec;
//import javax.crypto.spec.SecretKeySpec;
//import java.security.*;
//import java.security.spec.X509EncodedKeySpec;
//import java.util.Base64;
//import java.util.Random;
//
//public class MexcUtil {
//    private byte[] metaObj;
//    private String TK;
//
//    public void initAuth(String metaObj, String TK) {
//        this.metaObj = Base64.getDecoder().decode(metaObj);
//        this.TK = TK;
//    }
//
//    public static String chashs() {
//        Random random = new Random();
//        StringBuilder result = new StringBuilder();
//        String characters = "0123456789ABCDEF";
//        for (int i = 0; i < 32; i++) {
//            int randomIndex = random.nextInt(characters.length());
//            result.append(characters.charAt(randomIndex));
//        }
//        return result.toString().toLowerCase();
//    }
//
//    public String[] getOrderRequest(double equity, double percentPerTrade, String symbol, int lev, int side, String cont, String takeProfitPrice, String stopLossPrice, String type, String price) {
//        String chash = chashs();
//        byte[] key = new byte[32];
//        new SecureRandom().nextBytes(key);
//
//        String p0 = getP0(new String(metaObj), key);
//        String k0 = getK0(key);
//        long ts = System.currentTimeMillis();
//
//        String requestBody = "{\"symbol\":\"" + symbol + "\",\"side\":" + side + ",\"openType\":2,\"type\":\"" + type + "\",\"vol\":" + getVolume(equity, percentPerTrade, lev, cont) + ",\"leverage\":" + lev + ",\"marketCeiling\":false,\"priceProtect\":\"0\",\"p0\":\"" + p0 + "\",\"k0\":\"" + k0 + "\",\"chash\":\"" + chash + "\",\"mtoken\":\"" + Utils.mtoken + "\",\"ts\":" + ts + ",\"mhash\":\"" + Utils.mhash + "\"}";
//
//        if (type.equals("1")) {
//            requestBody += ",\"price\":\"" + price + "\"";
//        }
//        if (!takeProfitPrice.equals("")) {
//            requestBody += ",\"takeProfitPrice\":\"" + takeProfitPrice + "\"";
//        }
//        if (!stopLossPrice.equals("")) {
//            requestBody += ",\"stopLossPrice\":\"" + stopLossPrice + "\"";
//        }
//
//        String hash = getSign(requestBody, ts);
//
//        return new String[] {requestBody, "{\"ts\":" + ts + ",\"hash\":\"" + hash + "\"}"};
//    }
//
//    public static double getVolume(double equity, double percentPerTrade, int lev, double cont) {
//        // Calculate the volume based on the provided parameters
//        return (equity * percentPerTrade / 100) * lev / cont;
//    }
//
//    public String decryptAesGcm256(String encryptedMessageBase64, String keyHex) {
//        byte[] key = hexStringToByteArray(keyHex);
//        byte[] encryptedMessage = Base64.getDecoder().decode(encryptedMessageBase64);
//        byte[] iv = new byte[12];
//        System.arraycopy(encryptedMessage, 0, iv, 0, 12);
//        byte[] ciphertext = new byte[encryptedMessage.length - 28];
//        System.arraycopy(encryptedMessage, 12, ciphertext, 0, encryptedMessage.length - 28);
//        byte[] authTag = new byte[16];
//        System.arraycopy(encryptedMessage, encryptedMessage.length - 16, authTag, 0, 16);
//
//        try {
//            Cipher decipher = Cipher.getInstance("AES/GCM/NoPadding");
//            decipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
//            byte[] decrypted = decipher.doFinal(ciphertext);
//            return new String(decrypted);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
//    }
//
//    public String encryptAesGcm256(String plaintext, String keyHex) {
//        byte[] key = hexStringToByteArray(keyHex);
//        byte[] iv = new byte[12];
//        new SecureRandom().nextBytes(iv);
//
//        try {
//            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
//            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
//            byte[] encrypted = cipher.doFinal(plaintext.getBytes());
//            byte[] authTag = cipher.getParameters().getParameterSpec(GCMParameterSpec.class).getIV();
//            byte[] encryptedMessage = new byte[iv.length + encrypted.length + authTag.length];
//            System.arraycopy(iv, 0, encryptedMessage, 0, iv.length);
//            System.arraycopy(encrypted, 0, encryptedMessage, iv.length, encrypted.length);
//            System.arraycopy(authTag, 0, encryptedMessage, iv.length + encrypted.length, authTag.length);
//            return Base64.getEncoder().encodeToString(encryptedMessage);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
//    }
//
//    public String getP0(String plaintextObjectStr, byte[] key) {
//        return encryptAesGcm256(plaintextObjectStr, bytesToHex(key));
//    }
//
//    public String getK0(byte[] aesKey) {
//        String rsaPublicKeyBase64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAqqpMCeNv7qfsKe09xwE5o05ZCq/qJvTok6WbqYZOXA16UQqR+sHH0XXfnWxLSEvCviP9qjZjruHWdpMmC4i/yQJe7MJ66YoNloeNtmMgtqEIjOvSxRktmAxywul/eJolrhDnRPXYll4fA5+24t1g6L5fgo/p66yLtZRg4fC1s3rAF1WPe6dSJQx7jQ/xhy8Z0WojmzIeaoBa0m8qswx0DMIdzXfswH+gwMYCQGR3F/NAlxyvlWPMBlpFEuHZWkp9TXlTtbLf+YL8vYjV5HNqIdNjVzrIvg/Bis49ktfsWuQxT/RIyCsTEuHmZyZR6NJAMPZUE5DBnVWdLShb6KuyqwIDAQAB";
//        try {
//            byte[] publicKeyBytes = Base64.getDecoder().decode(rsaPublicKeyBase64);
//            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicKeyBytes);
//            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
//            PublicKey publicKey = keyFactory.generatePublic(keySpec);
//            java.security.Signature rsa = java.security.Signature.getInstance("NONEwithRSA");
//            rsa.initVerify(publicKey);
//            rsa.update(aesKey);
//            byte[] encryptedBytes = rsa.sign();
//
//            return Base64.getEncoder().encodeToString(encryptedBytes);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
//    }
//
//    public String getMd5(String string) {
//        try {
//            MessageDigest md = MessageDigest.getInstance("MD5");
//            byte[] hash = md.digest(string.getBytes());
//            StringBuilder hexString = new StringBuilder();
//            for (byte b : hash) {
//                String hex = Integer.toHexString(0xff & b);
//                if (hex.length() == 1) hexString.append('0');
//                hexString.append(hex);
//            }
//            return hexString.toString();
//        } catch (NoSuchAlgorithmException e) {
//            e.printStackTrace();
//            return null;
//        }
//    }
//
//    public String bytesToHex(byte[] bytes) {
//        StringBuilder hexString = new StringBuilder(2 * bytes.length);
//        for (byte b : bytes) {
//            String hex = Integer.toHexString(0xff & b);
//            if (hex.length() == 1) {
//                hexString.append('0');
//            }
//            hexString.append(hex);
//        }
//        return hexString.toString();
//    }
//
//    public long getTs() {
//        return System.currentTimeMillis();
//    }
//
//    public String getG(String TK, long ts) {
//        String md5 = getMd5(TK + ts);
//        return md5.substring(7) + "," + ts;
//    }
//
//    public String getSign(String formdata, long ts) {
//        String g = getG(TK, ts);
//        return getMd5(ts + formdata + g);
//    }
//
//    public static byte[] hexStringToByteArray(String s) {
//        int len = s.length();
//        byte[] data = new byte[len / 2];
//        for (int i = 0; i < len; i += 2) {
//            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
//                    + Character.digit(s.charAt(i + 1), 16));
//        }
//        return data;
//    }
//}
