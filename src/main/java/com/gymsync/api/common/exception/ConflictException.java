package com.gymsync.api.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {

  public ConflictException(String message) {
    super(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
  }
}
