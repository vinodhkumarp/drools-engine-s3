package com.example.retail.exception;

import com.example.retail.generated.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  /* 400 JSON body validation */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleBodyValidation(
      MethodArgumentNotValidException ex, HttpServletRequest req) {

    String msg =
        ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));

    return build(HttpStatus.BAD_REQUEST, msg, req);
  }

  /* 400 @Validated on query/path */
  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ErrorResponse> handleConstraint(
      ConstraintViolationException ex, HttpServletRequest req) {

    String msg =
        ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + " " + v.getMessage())
            .collect(Collectors.joining("; "));
    return build(HttpStatus.BAD_REQUEST, msg, req);
  }

  /* 404 Drools matched no rule */
  @ExceptionHandler(NoRuleMatchException.class)
  ResponseEntity<ErrorResponse> handleNoRule(NoRuleMatchException ex, HttpServletRequest req) {

    return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
  }

  /* 409 Domain conflict etc. */
  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<ErrorResponse> handleConflict(IllegalStateException ex, HttpServletRequest req) {

    return build(HttpStatus.CONFLICT, ex.getMessage(), req);
  }

  /* 500 catch-all */
  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> handleAny(Exception ex, HttpServletRequest req) {

    return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", req);
  }

  /* helper */
  private ResponseEntity<ErrorResponse> build(
      HttpStatus status, String msg, HttpServletRequest req) {

    return ResponseEntity.status(status)
        .body(
            new ErrorResponse()
                .timestamp(OffsetDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(msg));
  }
}
