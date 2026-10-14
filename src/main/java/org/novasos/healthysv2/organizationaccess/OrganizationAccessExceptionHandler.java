package org.novasos.healthysv2.organizationaccess;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import org.novasos.healthysv2.shared.api.error.ErrorResponse;
import org.novasos.healthysv2.shared.web.CorrelationIdFilter;
import org.springframework.core.annotation.Order;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes = OrganizationAccessController.class)
@Order(-1)
class OrganizationAccessExceptionHandler {
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ErrorResponse> invalid(HttpServletRequest request) {
    return response(HttpStatus.BAD_REQUEST, "INVALID_ORGANIZATION_ACCESS_REQUEST", request);
  }

  @ExceptionHandler(NoSuchElementException.class)
  ResponseEntity<ErrorResponse> missing(HttpServletRequest request) {
    return response(HttpStatus.NOT_FOUND, "ORGANIZATION_ACCESS_RESOURCE_NOT_FOUND", request);
  }

  @ExceptionHandler({
    OptimisticLockingFailureException.class,
    DataIntegrityViolationException.class
  })
  ResponseEntity<ErrorResponse> conflict(HttpServletRequest request) {
    return response(HttpStatus.CONFLICT, "ORGANIZATION_ROLE_CONFLICT", request);
  }

  private ResponseEntity<ErrorResponse> response(
      HttpStatus status, String code, HttpServletRequest request) {
    Object correlation = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
    return ResponseEntity.status(status)
        .body(
            new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                code,
                request.getRequestURI(),
                correlation == null ? null : correlation.toString(),
                List.of()));
  }
}
