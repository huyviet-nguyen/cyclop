package com.tbot.cyclop.orderplacer.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.model.*;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static com.tbot.cyclop.orderplacer.util.GenericHttpUtil.PUBLIC_KEY;
import static com.tbot.cyclop.orderplacer.util.PercentageUtil.*;
import static com.tbot.cyclop.orderplacer.util.PercentageUtil.calculateNewValue;

public class TradingUtil {

    private static final ObjectMapper mapper = new ObjectMapper();

    //checked
    public static boolean canSubmit(Strategy strategy, KlineData klineData, double maxDiffAbs) {
        double ignoreAmount = maxDiffAbs * strategy.getIgnore() / 100;
        double openPriceAfterIgnore = strategy.getPositionSide().equals("SHORT") ? klineData.getOpenPrice() + ignoreAmount : klineData.getOpenPrice() - ignoreAmount;
        double changePercent = calculateChangePercent(openPriceAfterIgnore, klineData.getCurrentPrice());
        boolean matchSide = (changePercent <= 0 && strategy.getPositionSide().equals("LONG")) || (changePercent > 0 && strategy.getPositionSide().equals("SHORT"));
        boolean matchPrice = Math.abs(changePercent) >= calculateNewValue(strategy.getOrderChange(), strategy.getExtendOrderChangePercent()) && Math.abs(changePercent) <= strategy.getOrderChange();
        return matchSide && matchPrice;
    }

    public static double calculateTakeProfitPrice(Strategy strategy, Order order) {
        double openPriceToOcOffset = Math.abs(order.getOpenOrderPrice() - order.getCandleOpenPrice());
        double takeProfitPriceOffset = Math.abs(calculateNewValue(openPriceToOcOffset, strategy.getTakeProfit()));
        if (strategy.getPositionSide().equals("LONG")) {
            return order.getOpenOrderPrice() + takeProfitPriceOffset;
        } else {
            return order.getOpenOrderPrice() - takeProfitPriceOffset;
        }

    }

    public static double calculateStopLossPrice(Strategy strategy, Order klineData) {
        double openPriceToOcOffset = Math.abs(klineData.getOpenOrderPrice() - klineData.getCandleOpenPrice());
        double stopLossPriceOffset = Math.abs(calculateNewValue(openPriceToOcOffset, strategy.getStopLoss()));
        if (strategy.getPositionSide().equals("LONG")) {
            return klineData.getOpenOrderPrice() - stopLossPriceOffset;
        } else {
            return klineData.getOpenOrderPrice() + stopLossPriceOffset;
        }
    }

    public static double calculateReduceUnitAmount(Strategy strategy, Order order) {
        return Math.abs(order.getCurrentTakeProfitPrice() - order.getOpenOrderPrice()) * strategy.getReduceTakeProfit() / 100;
    }


    public static double calculateReducedTakeProfitPrice(Strategy strategy, Order latestOrder, KlineData klineData) {
        switch (strategy.getReduceType()) {
            case "V1" -> {
                if (strategy.getPositionSide().equals("LONG")) {
                    return latestOrder.getCurrentTakeProfitPrice() - latestOrder.getReduceUnitAmount();
                } else {
                    return latestOrder.getCurrentTakeProfitPrice() + latestOrder.getReduceUnitAmount();
                }
            }
            case "V2" -> {
                double offset = (Math.abs(latestOrder.getCurrentTakeProfitPrice() - klineData.getOpenPrice()) * strategy.getReduceTakeProfit() / 100);
                return strategy.getPositionSide().equals("LONG") ? latestOrder.getCurrentTakeProfitPrice() - offset : latestOrder.getCurrentTakeProfitPrice() + offset;
            }
            default -> throw new IllegalArgumentException("Unknown reduce type");
        }
    }

    public static byte[] generateRandomBytes(int length) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] randomBytes = new byte[length];
        secureRandom.nextBytes(randomBytes);
        return randomBytes;
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    public static String getMexcK0(String aesKey) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(PUBLIC_KEY);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(keySpec);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedBytes = cipher.doFinal(aesKey.getBytes());

        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    public static String getMexcP0(FingerprintSysInfo sysInfo, byte[] keyBytes) throws Exception {
        byte[] jsonString = mapper.writeValueAsString(sysInfo).getBytes(StandardCharsets.UTF_8);
        byte[] iv = generateRandomBytes(12);

        SecretKey key = new SecretKeySpec(keyBytes, "AES");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));

        byte[] encrypted = cipher.doFinal(jsonString);
        byte[] authTag = cipher.getParameters().getParameterSpec(GCMParameterSpec.class).getIV();
        byte[] encryptedMessage = concatenateArrays(iv, encrypted, authTag);
        return Base64.getEncoder().encodeToString(encryptedMessage);
    }

    private static byte[] concatenateArrays(byte[]... arrays) {
        int totalLength = 0;
        for (byte[] array : arrays) {
            totalLength += array.length;
        }
        byte[] result = new byte[totalLength];
        int currentIndex = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, result, currentIndex, array.length);
            currentIndex += array.length;
        }
        return result;
    }

    public static String getMexcCHashs() {
        String characters = "0123456789ABCDEF";
        StringBuilder result = new StringBuilder();

        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 32; i++) {
            int randomIndex = random.nextInt(characters.length());
            result.append(characters.charAt(randomIndex));
        }

        return result.toString().toLowerCase();
    }

    public static int getMexcVolume(double balance, double strategyAmount, double cont) {
        if (strategyAmount < 0 || strategyAmount > 100) {
            throw new IllegalArgumentException("Percentage must be between 0 and 100.");
        }
        double equityFraction = strategyAmount / 100.0;
        double portfolioPortion = balance * equityFraction;
        double result = (portfolioPortion * 10) / cont;
        return (int) result;
    }

    public static int getBybitQuantity(double balance, double strategyAmount, double lev, double price) {
        double equityFraction = balance * strategyAmount / 100.0;
        return (int) (equityFraction * lev / price);
    }

    public static String getMexcSign(String payload, long ts, String apiKey) {
        String g = getMexcG(apiKey, ts)[0];
        String currentTs = String.valueOf(getMexcG(apiKey, ts)[1]);
        String hashInput = currentTs + payload + g;
        return getMd5(hashInput);
    }

    private static String[] getMexcG(String TK, long ts) {
        String combined = TK + ts;
        String md5Hash = getMd5(combined);
        String g = md5Hash.substring(7);
        return new String[]{g, String.valueOf(ts)};
    }

    private static String getMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found.", e);
        }
    }

    public static String getMapKey(KlineData value) {
        return value.getSymbol() + "." + value.getInterval();
    }

}
