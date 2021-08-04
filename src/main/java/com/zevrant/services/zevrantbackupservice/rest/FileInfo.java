package com.zevrant.services.zevrantbackupservice.rest;

public class FileInfo {

    private final String fileName;
    private final String hash;
    private final long id;
    private final long size;

    public FileInfo(String fileName, String hash, long id, long size) {
        this.fileName = fileName;
        this.hash = hash;
        this.id = id;
        this.size = size;
    }

    public String getFileName() {
        return fileName;
    }

    public String getHash() {
        return hash;
    }

    public long getId() {
        return id;
    }

    public long getSize() {
        return size;
    }
}
