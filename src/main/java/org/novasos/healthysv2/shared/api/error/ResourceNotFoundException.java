package org.novasos.healthysv2.shared.api.error;

import org.springframework.http.HttpStatus;

public final class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "error.resource.not-found",
                resource,
                identifier);
    }
}
