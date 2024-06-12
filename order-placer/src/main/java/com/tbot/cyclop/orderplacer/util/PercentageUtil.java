package com.tbot.cyclop.orderplacer.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

public class PercentageUtil {
    public static double addPercentage(double value, double percent) {
        return value * (1 + percent / 100);
    }

    // Method to deduct a percentage from a value
    public static double deductPercentage(double value, double percent) {
        return value * (1 - percent / 100);
    }

    // Method to calculate the percentage of a value relative to another value
    public static double calculatePercent(double percent, double whole) {
        if (whole == 0) {
            throw new IllegalArgumentException("Cannot calculate percentage with zero as the denominator.");
        }
        return (percent / whole) * 100;
    }

    // Method to calculate the percentage change from the initial value to the final value
    public static double calculateChangePercent(double initialValue, double finalValue) {
        if (initialValue == 0) {
            return 0;
        }
        return ((finalValue - initialValue) / initialValue) * 100;
    }

    public static double calculateNewValue(double originalNumber, double percent) {
        return originalNumber * percent / 100;
    }

    public static double formatToTwoDecimal(double value) {
        BigDecimal bd = new BigDecimal(Double.toString(value));
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public static double roundToSameDecimal(double pu, double value) {
        // Calculate the number of decimal places in pu
        int decimalPlaces = countDecimalPlaces(pu);

        // Round up the value to the same number of decimal places
        return round(value, decimalPlaces);
    }

    // Method to count the number of decimal places in a double value
    private static int countDecimalPlaces(double value) {
        String valueStr = normalizeDouble(value);
        int index = valueStr.indexOf('.');
        return index < 0 ? 0 : valueStr.length() - index - 2;
    }

    // Method to round a double value to a specified number of decimal places
    private static double round(double value, int decimalPlaces) {
        double scale = Math.pow(10, decimalPlaces);
        return Math.ceil(value * scale) / scale;
    }


    public static String normalizeDouble(double input){
        DecimalFormat decimalFormat = new DecimalFormat("0.#################################");
        // Format the number
        return decimalFormat.format(input);
    }
}
