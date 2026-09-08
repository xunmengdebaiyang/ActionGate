package com.actiongate.api;

import com.actiongate.contract.ContractViolationException;
import io.grpc.StatusRuntimeException;
import io.temporal.client.WorkflowServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ContractViolationException.class)
    ResponseEntity<ProblemDetail> invalidTicket(ContractViolationException exception) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid synthetic ticket");
        detail.setProperty("code", "INVALID_TICKET");
        detail.setProperty("violations", exception.violations());
        return ResponseEntity.badRequest().body(detail);
    }

    @ExceptionHandler({StatusRuntimeException.class, WorkflowServiceException.class})
    ResponseEntity<ProblemDetail> temporalUnavailable(Exception exception) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "Temporal could not confirm the operation. A submitted run may still have been accepted.");
        detail.setProperty("code", "TEMPORAL_UNAVAILABLE");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(detail);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> runStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason()));
    }
}
