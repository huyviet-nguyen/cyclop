package com.tbot.cyclop.orderplacer.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tbot.cyclop.Cyclop.dto.KlineData;
import com.tbot.cyclop.Cyclop.dto.req.MexcChangePriceRequest;
import com.tbot.cyclop.Cyclop.dto.req.MexcOpenOrderRequest;
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

    private static final double ONE_HUNDRED_PERCENT = 100;

    private static final ObjectMapper mapper = new ObjectMapper();

    public static boolean canIgnore(Strategy strategy, KlineData klineData) {
        double lastPump = strategy.getCandleWindow().getLastPump();
        double ignorePercent = calculateNewValue(lastPump, strategy.getIgnore());
        double changePercent = calculateChangePercent(klineData.getOpenPrice(), klineData.getCurrentPrice());
        return Math.abs(changePercent) < Math.abs(ignorePercent);
    }

    public static boolean canEntry(Strategy strategy, KlineData klineData) {
        double changePercent = calculateChangePercent(klineData.getOpenPrice(), klineData.getCurrentPrice());
        double expectedSide = switch (strategy.getPositionSide()) {
            case "LONG" -> 1;
            case "SHORT" -> -1;
            default -> 0; // for BOTH
        };
        double expectedChangePercent = calculateNewValue(strategy.getOrderChange(), strategy.getExtendOrderChangePercent());
        if (expectedSide == 0) {
            return Math.abs(changePercent) > expectedChangePercent;
        }
        return changePercent >= expectedChangePercent * expectedSide;
    }

    public static double calculateTakeProfitProportion(Strategy strategy) {
        if (strategy.getPositionSide().equals("LONG")) {
            return calculateNewValue(strategy.getOrderChange(), strategy.getTakeProfit()) + ONE_HUNDRED_PERCENT;
        } else {
            return ONE_HUNDRED_PERCENT - calculateNewValue(strategy.getOrderChange(), strategy.getTakeProfit());
        }
    }

    public static double calculateTakeProfitPrice(Strategy strategy, KlineData klineData) {
        double takeProfitPercent = calculateTakeProfitProportion(strategy);
        return calculateNewValue(klineData.getCurrentPrice(), takeProfitPercent);
    }

    public static double calculateStopLossProportion(Strategy strategy) {
        if (strategy.getPositionSide().equals("SHORT")) {
            return calculateNewValue(strategy.getOrderChange(), strategy.getStopLoss()) + ONE_HUNDRED_PERCENT;
        } else {
            return ONE_HUNDRED_PERCENT - calculateNewValue(strategy.getOrderChange(), strategy.getStopLoss());
        }
    }

    public static double calculateStopLossPrice(Strategy strategy, KlineData klineData) {
        double stopLossPercent = calculateStopLossProportion(strategy);
        return calculateNewValue(klineData.getCurrentPrice(), stopLossPercent);
    }

    public static boolean canTakeProfit(OrderAckHistory latestOrder, KlineData klineData) {
        return latestOrder != null && klineData.getCurrentPrice() > latestOrder.getCurrentTakeProfitPrice();
    }

    public static boolean canStopLoss(OrderAckHistory latestOrder, KlineData klineData) {
        return latestOrder != null && klineData.getCurrentPrice() < latestOrder.getStopLossPrice();
    }

    public static boolean newCandle(Strategy strategy, KlineData klineData) {
        CandleWindow candleWindow = strategy.getCandleWindow();
        if (candleWindow == null) return true;
        double lastOpenPrice = candleWindow.getOpenPrice();
        return lastOpenPrice != klineData.getOpenPrice();
    }

    public static double calculateReducedTakeProfitPrice(Strategy strategy, KlineData klineData, OrderAckHistory latestOrder) {
        double lastTakeProfitPercent = latestOrder.getLastTakeProfitPercent();
        double newTakeProfitPercent = deductPercentage(lastTakeProfitPercent, strategy.getReduceTakeProfit());
        double newTakeProfitPrice = 0;
        if (strategy.getPositionSide().equals("LONG")) {
            newTakeProfitPrice = calculateNewValue(klineData.getCurrentPrice(), ONE_HUNDRED_PERCENT + newTakeProfitPercent);
        } else {
            newTakeProfitPrice = calculateNewValue(klineData.getCurrentPrice(), ONE_HUNDRED_PERCENT - newTakeProfitPercent);
        }
        return newTakeProfitPrice;
    }

    public static double calculateLastTakeProfitPercent(Strategy strategy, KlineData klineData, OrderAckHistory latestOrder){
        double lastTakeProfitPercent = latestOrder.getLastTakeProfitPercent();
        return deductPercentage(lastTakeProfitPercent, strategy.getReduceTakeProfit());
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

    public static int getVolume(double balance, double strategyAmount, double cont) {
        if (strategyAmount < 0 || strategyAmount > 100) {
            throw new IllegalArgumentException("Percentage must be between 0 and 100.");
        }
        double equityFraction = strategyAmount / 100.0;
        double portfolioPortion = balance * equityFraction;
        double result = (portfolioPortion * 10) / cont;
        return (int) result;
    }

    public static String getMexcSign(MexcOpenOrderRequest request, long ts, String apiKey) throws JsonProcessingException {
        String formdata = request != null ? mapper.writeValueAsString(request) : "";
        String g = getMexcG(apiKey, ts)[0];
        String currentTs = String.valueOf(getMexcG(apiKey, ts)[1]);
        String hashInput = currentTs + formdata + g;
        return getMd5(hashInput);
    }

    public static String getMexcSign(String request, long ts, String apiKey) throws JsonProcessingException {
        String g = getMexcG(apiKey, ts)[0];
        String currentTs = String.valueOf(getMexcG(apiKey, ts)[1]);
        String hashInput = currentTs + request + g;
        return getMd5(hashInput);
    }

    public static String getMexcSign(MexcChangePriceRequest request, long ts, String apiKey) throws JsonProcessingException {
        String formdata = request != null ? mapper.writeValueAsString(request) : "";
        String g = getMexcG(apiKey, ts)[0];
        String currentTs = String.valueOf(getMexcG(apiKey, ts)[1]);
        String hashInput = currentTs + formdata + g;
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

    public static String replaceUsdtSuffix(String input) {
        return input.substring(0, input.length() - 4).concat("_USDT");
    }

    public static String addCandleStickPrefix(String input) {
        return "M".concat(input);
    }


}
