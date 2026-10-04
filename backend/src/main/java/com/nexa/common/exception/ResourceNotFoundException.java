package com.nexa.common.exception;

import com.nexa.common.api.ErrorCode;

public class ResourceNotFoundException extends NexaException {

    public ResourceNotFoundException(String resourceType, Object id) {
        super(ErrorCode.NOT_FOUND, "%s %s was not found".formatted(resourceType, id));
    }
}
