package com.zevrant.services.zevrantbackupservice.controllers;

import com.zevrant.services.zevrantbackupservice.exceptions.BackupFileNotFoundException;
import com.zevrant.services.zevrantbackupservice.rest.BackupFileRequest;
import com.zevrant.services.zevrantbackupservice.rest.BackupFileResponse;
import com.zevrant.services.zevrantbackupservice.rest.CheckExistence;
import com.zevrant.services.zevrantbackupservice.services.FileService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/file-backup")

public class FileBackupController {

    private static final Logger logger = LoggerFactory.getLogger(FileBackupController.class);
    private final FileService fileService;
    private final List<String> activeProfiles;

    @Autowired
    public FileBackupController(FileService fileService, RestTemplate restTemplate,
                                @Value("${spring.profiles.active}") String activeProfiles) {
        this.fileService = fileService;
        this.activeProfiles = Arrays.asList(activeProfiles.split(","));
    }

    /**
     * Takes a list of image info and returns those that do not exist
     *
     * @param checkExistence object containing a list of FileInfo
     * @return an object containing a list of FileInfo for files not currently backed up
     */
    @ResponseBody
    @PostMapping("/check-existence")
    @PreAuthorize("hasAuthority('backups')")
    public Mono<CheckExistence> checkIfExists(@RequestBody CheckExistence checkExistence) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> new CheckExistence(
                        fileService.filterExisting(checkExistence.getFileInfos(),
                                getUsername(securityContext))));
    }

    @ResponseBody
    @PutMapping
    @PreAuthorize("hasAuthority('backups')")
    public Mono<BackupFileResponse> backupFile(@RequestBody BackupFileRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .publishOn(Schedulers.boundedElastic())
                .map(securityContext -> {
                    fileService.backupFile(
                            getUsername(securityContext),
                            request.getFileInfo(),
                            request.getSerializedFileData());

                    return new BackupFileResponse();
                });


    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('backups')")
    public @ResponseBody
    Mono<List<String>> removeStorage(@RequestBody(required = false) CheckExistence request) {
        return ReactiveSecurityContextHolder.getContext()
                .publishOn(Schedulers.boundedElastic())
                .map(securityContext -> {
                    String username = getUsername(securityContext);
                    List<String> deleted = new ArrayList<>();
                    if (activeProfiles.contains("local") || activeProfiles.contains("develop") && request == null) {
                        return fileService.removeStorageFor(username);
                    }
                    fileService.filterExisting((request != null) ? request.getFileInfos() : Collections.emptyList(), username)
                            .forEach(fileInfo -> {
                                String fileHash = "";
                                try {
                                    String[] fileNamePieces = fileInfo.getFileName().split("\\.");
                                    fileHash = fileService.deleteBackupFile(fileInfo.getFileName(),
                                            fileService.getImageTypeDir(fileNamePieces[fileNamePieces.length - 1], username));
                                } catch (BackupFileNotFoundException | IOException ex) {
                                    logger.info("Failed to delete file {} with hash {}", fileInfo.getFileName(), fileInfo.getHash());
                                }

                                if (StringUtils.isNotBlank(fileHash)) {
                                    deleted.add(fileInfo.getFileName());
                                }
                            });
                    return deleted;
                });
    }

    @GetMapping
    @PreAuthorize("hasAuthority('backups')")
    public @ResponseBody
    Mono<List<String>> getHashesForBackupFiles() {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> fileService.getHashesFor(getUsername(securityContext)));
    }

    private String getUsername(SecurityContext securityContext) {
        return ((Jwt) securityContext.getAuthentication().getPrincipal())
                .getClaim("preferred_username");
    }
}
