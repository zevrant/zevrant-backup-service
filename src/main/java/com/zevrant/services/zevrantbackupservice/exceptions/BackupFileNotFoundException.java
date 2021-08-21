package com.zevrant.services.zevrantbackupservice.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class BackupFileNotFoundException extends RuntimeException {

    public BackupFileNotFoundException() {
    }

    public BackupFileNotFoundException(String message) {
        super(message);
    }
}
