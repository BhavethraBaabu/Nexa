package com.nexa.common.exception;

import com.nexa.common.api.ErrorCode;

public class UnauthorizedException extends NexaException {

    public UnauthorizedException(String message) {
        super(ErrorCode.UNAUTHORIZED, message);
    }
}
