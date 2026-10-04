package com.nexa.common.exception;

import com.nexa.common.api.ErrorCode;

public class BusinessRuleException extends NexaException {

    public BusinessRuleException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, message);
    }
}
