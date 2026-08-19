package com.capstone.deepterview.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
	FORBIDDEN(HttpStatus.FORBIDDEN),
	NOT_FOUND(HttpStatus.NOT_FOUND),
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
	CONFLICT(HttpStatus.CONFLICT),
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
	SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

	private final HttpStatus httpStatus;

	ErrorCode(HttpStatus httpStatus) {
		this.httpStatus = httpStatus;
	}

	public HttpStatus getHttpStatus() {
		return httpStatus;
	}
}

