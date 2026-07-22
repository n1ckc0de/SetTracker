package com.gymsync.api.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApiException {

  public ForbiddenException(String message) {
    super(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
  }
}
