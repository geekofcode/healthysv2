package org.novasos.healthysv2.shared.api.error;

import org.springframework.http.HttpStatus;

public final class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
