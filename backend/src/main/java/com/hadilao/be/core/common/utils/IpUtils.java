package com.hadilao.be.core.common.utils;

import jakarta.servlet.http.HttpServletRequest;

public class IpUtils {
    public static String getClientIpAddress(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
