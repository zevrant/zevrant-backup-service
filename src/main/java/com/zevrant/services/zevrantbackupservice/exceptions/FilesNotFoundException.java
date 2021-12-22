package com.zevrant.services.zevrantbackupservice.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class FilesNotFoundException extends RuntimeException {

    public FilesNotFoundException() {
        super("The Requested Files Could Not Be Found.");
    }
}
