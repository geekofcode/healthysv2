package org.novasos.healthysv2.shared.api.error;

import org.springframework.http.HttpStatus;

public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Object[] messageArguments;

    protected ApiException(HttpStatus status, String code, String messageKey, Object... messageArguments) {
        super(messageKey);
        this.status = status;
        this.code = code;
        this.messageArguments = messageArguments.clone();
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String messageKey() { return getMessage(); }
    public Object[] messageArguments() { return messageArguments.clone(); }
}
