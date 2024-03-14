package com.tbot.cyclop.orderplacer.util;

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
            throw new IllegalArgumentException("Cannot calculate change percent with zero as the initial value.");
        }
        return ((finalValue - initialValue) / initialValue) * 100;
    }

    public static double calculateNewValue(double originalNumber, double percent) {
        return originalNumber * percent / 100;
    }
}
