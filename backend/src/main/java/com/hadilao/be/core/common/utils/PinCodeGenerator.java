package com.hadilao.be.core.common.utils;

import java.security.SecureRandom;

public class PinCodeGenerator {
    private static final String PREFIX = "RML-";
    private static final int PIN_LENGTH = 6;
    private static final SecureRandom random = new SecureRandom();

    public static String generate() {
        StringBuilder sb = new StringBuilder(PREFIX);
        for (int i = 0; i < PIN_LENGTH; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
