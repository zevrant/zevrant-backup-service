package com.zevrant.services.zevrantbackupservice.rest;

public class BackupFileResponse {

    private String responseMessage = "Success";

    public BackupFileResponse() {
    }

    public BackupFileResponse(String responseMessage) {
        this.responseMessage = responseMessage;
    }

    public String getResponseMessage() {
        return responseMessage;
    }

    public void setResponseMessage(String responseMessage) {
        this.responseMessage = responseMessage;
    }
}
