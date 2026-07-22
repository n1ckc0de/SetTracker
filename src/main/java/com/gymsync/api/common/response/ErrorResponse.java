package com.gymsync.api.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gymsync.api.common.exception.ErrorCode;
import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    ErrorCode code,
    String message,
    String path,
    Map<String, String> fieldErrors,
    String correlationId) {

  public static ErrorResponse of(
      int status,
      String error,
      ErrorCode code,
      String message,
      String path,
      Map<String, String> fieldErrors,
      String correlationId) {
    return new ErrorResponse(
        Instant.now(), status, error, code, message, path, fieldErrors, correlationId);
  }
}
