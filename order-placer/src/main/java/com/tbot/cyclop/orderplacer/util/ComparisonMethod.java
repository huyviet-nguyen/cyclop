package com.tbot.cyclop.orderplacer.util;

@FunctionalInterface
public interface ComparisonMethod<T> {
    boolean compare(T a, T b);
}
