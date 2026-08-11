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

    public static class User {
        public static final String ME = "/users/me";
        public static final String PROFILE = "/users/me/profile";
        public static final String AVATAR = "/users/me/avatar";
    }

    public static class Friend {
        public static final String BASE = "/friends";
        public static final String SEARCH = "/friends/search";
        public static final String REQUESTS = "/friends/requests";
        public static final String INCOMING_REQUESTS = "/friends/requests/incoming";
        public static final String OUTGOING_REQUESTS = "/friends/requests/outgoing";
        public static final String ACCEPT_REQUEST = "/friends/requests/{id}/accept";
        public static final String REQUEST_BY_ID = "/friends/requests/{id}";
        public static final String FRIENDSHIP_BY_ID = "/friends/{friendshipId}";
    }

    public static class Chat {
        public static final String FRIEND_MESSAGES = "/chat/friends/{friendId}/messages";
    }

    public static class Plan {
        public static final String BASE = "/plans";
        public static final String SYNC = "/plans/sync";
        public static final String BY_ID = "/plans/{planId}";
        public static final String INVITATIONS = "/plans/{planId}/invitations";
        public static final String MEMBER = "/plans/{planId}/members/{userId}";
        public static final String MEMBERSHIP = "/plans/{planId}/membership";
    }

    public static class PlanInvitation {
        public static final String INCOMING = "/plan-invitations/incoming";
        public static final String OUTGOING = "/plan-invitations/outgoing";
        public static final String ACCEPT = "/plan-invitations/{id}/accept";
        public static final String DECLINE = "/plan-invitations/{id}/decline";
        public static final String BY_ID = "/plan-invitations/{id}";
    }
}
