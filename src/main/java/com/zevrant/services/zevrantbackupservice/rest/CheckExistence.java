package com.zevrant.services.zevrantbackupservice.rest;

import java.util.List;

public class CheckExistence {

    private List<FileInfo> fileInfos;

    public CheckExistence() {
    }


    public CheckExistence(List<FileInfo> fileInfos) {
        this.fileInfos = fileInfos;
    }

    public List<FileInfo> getFileInfos() {
        return fileInfos;
    }

    public void setFileInfos(List<FileInfo> fileInfos) {
        this.fileInfos = fileInfos;
    }
}
