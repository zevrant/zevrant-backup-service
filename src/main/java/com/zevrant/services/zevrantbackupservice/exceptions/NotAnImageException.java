package com.zevrant.services.zevrantbackupservice.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class NotAnImageException extends RuntimeException {

    public NotAnImageException(String message) {
        super(message);
    }
}
