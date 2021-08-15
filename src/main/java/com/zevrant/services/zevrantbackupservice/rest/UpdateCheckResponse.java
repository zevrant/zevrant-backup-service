package com.zevrant.services.zevrantbackupservice.rest;

public class UpdateCheckResponse {
    private String suppliedVersion;
    private String latestVersion;
    private boolean newVersionAvailable;

    public UpdateCheckResponse() {
    }

    public UpdateCheckResponse(String suppliedVersion, String latestVersion, boolean newVersionAvailable) {
        this.suppliedVersion = suppliedVersion;
        this.latestVersion = latestVersion;
        this.newVersionAvailable = newVersionAvailable;
    }

    public String getSuppliedVersion() {
        return suppliedVersion;
    }

    public void setSuppliedVersion(String suppliedVersion) {
        this.suppliedVersion = suppliedVersion;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(String latestVersion) {
        this.latestVersion = latestVersion;
    }

    public boolean isNewVersionAvailable() {
        return newVersionAvailable;
    }

    public void setNewVersionAvailable(boolean newVersionAvailable) {
        this.newVersionAvailable = newVersionAvailable;
    }
}
