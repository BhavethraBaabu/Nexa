package com.nexa.common.exception;

import com.nexa.common.api.ErrorCode;

public class ConflictException extends NexaException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
