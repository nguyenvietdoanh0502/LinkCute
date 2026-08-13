package com.hadilao.be.modules.auth.service;

import com.hadilao.be.core.exception.AppException;
import com.hadilao.be.core.exception.ErrorCode;

public class RefreshTokenRejectedException extends AppException {

    private final boolean clearCookie;

    public RefreshTokenRejectedException(boolean clearCookie) {
        super(ErrorCode.INVALID_REFRESH_TOKEN);
        this.clearCookie = clearCookie;
    }

    public boolean shouldClearCookie() {
        return clearCookie;
    }
}
