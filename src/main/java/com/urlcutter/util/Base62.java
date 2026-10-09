package com.urlcutter.util;

public class Base62 { 
    public static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Value must be non-negative");
        }
        if (value == 0) {
            return "0";
        }
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            int digit = (int) (value % BASE62.length());
            sb.append(BASE62.charAt(digit));
            value /= BASE62.length();
        }
        return sb.reverse().toString();
    }
}
