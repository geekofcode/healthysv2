package org.novasos.healthysv2.professional;

import org.novasos.healthysv2.shared.api.error.ApiException;
import org.springframework.http.HttpStatus;

/** Invalid user input for the professional registration workflow. */
final class ProfessionalInputException extends ApiException {
    ProfessionalInputException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
