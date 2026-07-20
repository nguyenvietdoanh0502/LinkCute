package com.hadilao.be.core.constant;

public class UrlConstant {

    public static final String API_V1 = "/api/v1";

    public static class Auth {
        public static final String REGISTER = "/auth/register";
        public static final String VERIFY_OTP = "/auth/verify-otp";
        public static final String LOGIN = "/auth/login";
        public static final String REFRESH_TOKEN = "/auth/refresh-token";
        public static final String LOGOUT = "/auth/logout";
        public static final String CHANGE_PASSWORD = "/auth/change-password";
        public static final String FORGOT_PASSWORD = "/auth/forgot-password";
        public static final String VERIFY_OTP_FORGOT_PASSWORD = "/auth/verify-otp-forgot-password";
        public static final String RESET_PASSWORD = "/auth/reset-password";
    }

    public static class Place {
        public static final String BASE = "/places";
        public static final String MAP = "/places/map";
        public static final String DETAIL = "/places/{id}";
        public static final String CATEGORIES = "/categories";
        public static final String DISTRICTS = "/districts";
        public static final String IMPORT = "/places/import";
        public static final String IMPORT_OPEN_DATA = "/places/import/open-data";
        public static final String IMPORT_OVERTURE = "/places/import/overture";
        public static final String IMPORT_OSM = "/places/import/osm";
    }
}
