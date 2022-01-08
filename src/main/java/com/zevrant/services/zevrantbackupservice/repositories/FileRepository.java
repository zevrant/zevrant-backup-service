package com.zevrant.services.zevrantbackupservice.repositories;

import com.zevrant.services.zevrantbackupservice.entities.BackupFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<BackupFile, String> {

    @Query("select file from BackupFile file where file.id in :hashes and file.uploadedBy = :user")
    List<BackupFile> findHashesByUser(@Param("hashes") List<String> hashes, @Param("user") String user);

    List<BackupFile> deleteBackupFileByUploadedBy(String uploadedBy);

    List<BackupFile> findAllByUploadedBy(String uploadedBy);

    Page<BackupFile> findAllByUploadedBy(String uploadedBy, Pageable pageRequest);

    Integer countAllByUploadedBy(String uploadedBy);

    boolean existsByIdAndUploadedBy(String id, String uploadedBy);

    Optional<BackupFile> findBackupFileByIdAndUploadedBy(String id, String uploadedBy);
}
