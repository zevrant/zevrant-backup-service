package com.zevrant.services.zevrantbackupservice.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "FILE")
public class BackupFile {

    @Id
    @Column(name = "ID")
    private String id;

    @Column(name = "filepath", nullable = false, unique = true)
    private String filePath;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;

    public BackupFile() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
}
