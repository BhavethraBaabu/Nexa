package com.nexa.common.exception;

import com.nexa.common.api.ErrorCode;

/** A request that is well-formed but cannot be processed as sent; the message is shown to the user. */
public class InvalidRequestException extends NexaException {

    public InvalidRequestException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }
}
