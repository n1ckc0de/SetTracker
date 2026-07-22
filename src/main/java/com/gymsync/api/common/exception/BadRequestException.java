package com.gymsync.api.common.exception;

import org.springframework.http.HttpStatus;

public class BadRequestException extends ApiException {

  public BadRequestException(String message) {
    super(ErrorCode.BAD_REQUEST, HttpStatus.BAD_REQUEST, message);
  }
}
