package com.zevrant.services.zevrantbackupservice.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class FailedToProcessFileException extends RuntimeException {

    public FailedToProcessFileException() {
    }

    public FailedToProcessFileException(String message) {
        super(message);
    }
}
