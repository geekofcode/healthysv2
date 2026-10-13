package org.novasos.healthysv2.licensing;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.novasos.healthysv2.shared.api.error.ErrorResponse;
import org.novasos.healthysv2.shared.web.CorrelationIdFilter;
import org.springframework.core.annotation.Order;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = {LicensingController.class, StripeWebhookController.class})
@Order(-1)
class LicensingExceptionHandler {
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ErrorResponse> invalid(
      IllegalArgumentException exception, HttpServletRequest request) {
    String code =
        exception.getMessage() == null ? "INVALID_LICENSING_REQUEST" : exception.getMessage();
    return response(HttpStatus.BAD_REQUEST, code, "Invalid licensing request: " + code, request);
  }

  @ExceptionHandler(EmptyResultDataAccessException.class)
  ResponseEntity<ErrorResponse> missing(HttpServletRequest request) {
    return response(
        HttpStatus.NOT_FOUND, "LICENSING_RESOURCE_NOT_FOUND", "Resource not found", request);
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<ErrorResponse> unavailable(HttpServletRequest request) {
    return response(
        HttpStatus.SERVICE_UNAVAILABLE,
        "LICENSING_PROVIDER_UNAVAILABLE",
        "Subscription provider unavailable",
        request);
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status, String code, String message, HttpServletRequest request) {
    Object correlation = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
    return ResponseEntity.status(status)
        .body(
            new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                correlation == null ? null : correlation.toString(),
                List.of()));
  }
}
