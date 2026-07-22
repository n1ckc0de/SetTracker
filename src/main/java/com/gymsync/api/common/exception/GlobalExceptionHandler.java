package com.gymsync.api.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.gymsync.api.common.filter.CorrelationIdFilter;
import com.gymsync.api.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApiException(
      ApiException ex, HttpServletRequest request) {
    log.warn("API error [{}]: {}", ex.getErrorCode(), ex.getMessage());
    return buildResponse(
        ex.getStatus(),
        ex.getStatus().getReasonPhrase(),
        ex.getErrorCode(),
        ex.getMessage(),
        request,
        null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
      fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
    }
    log.warn("Validation failed for {}: {}", request.getRequestURI(), fieldErrors);
    return buildResponse(
        HttpStatus.BAD_REQUEST,
        "Validation Failed",
        ErrorCode.VALIDATION_ERROR,
        "One or more fields are invalid.",
        request,
        fieldErrors);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    ex.getConstraintViolations()
        .forEach(
            violation ->
                fieldErrors.put(violation.getPropertyPath().toString(), violation.getMessage()));
    return buildResponse(
        HttpStatus.BAD_REQUEST,
        "Validation Failed",
        ErrorCode.VALIDATION_ERROR,
        "One or more fields are invalid.",
        request,
        fieldErrors);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleMalformedJson(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    String message = "Malformed JSON request body.";
    if (ex.getCause() instanceof InvalidFormatException invalidFormat) {
      message = "Invalid value for field: " + invalidFormat.getPathReference();
    }
    log.warn("Malformed JSON at {}: {}", request.getRequestURI(), ex.getMessage());
    return buildResponse(
        HttpStatus.BAD_REQUEST, "Bad Request", ErrorCode.MALFORMED_JSON, message, request, null);
  }

  @ExceptionHandler(AuthenticationException.class)
  public ResponseEntity<ErrorResponse> handleAuthentication(
      AuthenticationException ex, HttpServletRequest request) {
    return buildResponse(
        HttpStatus.UNAUTHORIZED,
        "Unauthorized",
        ErrorCode.UNAUTHORIZED,
        "Authentication is required.",
        request,
        null);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    return buildResponse(
        HttpStatus.FORBIDDEN,
        "Forbidden",
        ErrorCode.FORBIDDEN,
        "You do not have permission to access this resource.",
        request,
        null);
  }

  @ExceptionHandler(NoHandlerFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(
      NoHandlerFoundException ex, HttpServletRequest request) {
    return buildResponse(
        HttpStatus.NOT_FOUND,
        "Not Found",
        ErrorCode.NOT_FOUND,
        "The requested endpoint was not found.",
        request,
        null);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    String message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'.";
    return buildResponse(
        HttpStatus.BAD_REQUEST, "Bad Request", ErrorCode.BAD_REQUEST, message, request, null);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleIllegalArgument(
      IllegalArgumentException ex, HttpServletRequest request) {
    log.warn("Illegal argument at {}: {}", request.getRequestURI(), ex.getMessage());
    return buildResponse(
        HttpStatus.BAD_REQUEST,
        "Bad Request",
        ErrorCode.BAD_REQUEST,
        ex.getMessage(),
        request,
        null);
  }

  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<ErrorResponse> handleOptimisticLock(
      ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
    log.warn("Optimistic locking conflict at {}: {}", request.getRequestURI(), ex.getMessage());
    return buildResponse(
        HttpStatus.CONFLICT,
        "Conflict",
        ErrorCode.CONFLICT,
        "The resource was modified concurrently. Please retry.",
        request,
        null);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    log.warn(
        "Data integrity violation at {}: {}",
        request.getRequestURI(),
        ex.getMostSpecificCause().getMessage());
    return buildResponse(
        HttpStatus.CONFLICT,
        "Conflict",
        ErrorCode.CONFLICT,
        "The request could not be completed due to a data conflict.",
        request,
        null);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
    return buildResponse(
        HttpStatus.METHOD_NOT_ALLOWED,
        "Method Not Allowed",
        ErrorCode.BAD_REQUEST,
        "HTTP method " + ex.getMethod() + " is not supported for this endpoint.",
        request,
        null);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unexpected error at {}", request.getRequestURI(), ex);
    return buildResponse(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Internal Server Error",
        ErrorCode.INTERNAL_ERROR,
        "An unexpected error occurred.",
        request,
        null);
  }

  private ResponseEntity<ErrorResponse> buildResponse(
      HttpStatus status,
      String error,
      ErrorCode code,
      String message,
      HttpServletRequest request,
      Map<String, String> fieldErrors) {
    String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
    ErrorResponse body =
        ErrorResponse.of(
            status.value(),
            error,
            code,
            message,
            request.getRequestURI(),
            fieldErrors,
            correlationId);
    return ResponseEntity.status(status).body(body);
  }
}
