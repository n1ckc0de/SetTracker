package com.gymsync.api.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

  public NotFoundException(String message) {
    super(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, message);
  }
}
