package org.novasos.healthysv2.profilecontract;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import org.novasos.healthysv2.shared.api.error.ErrorResponse;
import org.novasos.healthysv2.shared.web.CorrelationIdFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(
    assignableTypes = {ProfileContractController.class, ContractTemplateController.class})
@Order(-1)
class ContractExceptionHandler {
  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ErrorResponse> invalid(IllegalArgumentException e, HttpServletRequest request) {
    String code = e.getMessage() == null ? "INVALID_CONTRACT_REQUEST" : e.getMessage();
    Object correlation = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
    return ResponseEntity.badRequest()
        .body(
            new ErrorResponse(
                Instant.now(),
                400,
                "Bad Request",
                code,
                code,
                request.getRequestURI(),
                correlation == null ? null : correlation.toString(),
                List.of()));
  }
}
