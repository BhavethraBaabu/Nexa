package com.nexa.common.exception;

import com.nexa.common.api.ErrorCode;

public class InvalidTokenException extends NexaException {

    public InvalidTokenException(String message) {
        super(ErrorCode.INVALID_TOKEN, message);
    }
}
