package com.zevrant.services.zevrantbackupservice.controllers;

import com.zevrant.services.zevrantbackupservice.exceptions.BackupFileNotFoundException;
import com.zevrant.services.zevrantbackupservice.exceptions.MethodNotAllowedException;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
                                securityContext.getAuthentication().getPrincipal().toString())));
    }

    @ResponseBody
    @PutMapping
    @PreAuthorize("hasAuthority('backups')")
    public Mono<BackupFileResponse> backupFile(@RequestBody BackupFileRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> {
                    fileService.backupFile(securityContext.getAuthentication().getPrincipal().toString(), request.getFileInfo(), request.getSerializedFileData());
                    return new BackupFileResponse();
                });


    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('backups')")
    public @ResponseBody
    Mono<List<String>> removeStorage(@RequestBody(required = false) Optional<CheckExistence> request) {
        return request.map(checkExistence -> ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> {
                    String username = securityContext.getAuthentication().getPrincipal().toString();
                    List<String> deleted = new ArrayList<>();
                    fileService.filterExisting(checkExistence.getFileInfos(), username)
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
                })).orElseGet(() -> ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> {
                    if (activeProfiles.contains("local") || activeProfiles.contains("develop")) {
                        return fileService.removeStorageFor(securityContext.getAuthentication().getPrincipal().toString());
                    } else {
                        throw new MethodNotAllowedException();
                    }
                }));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('backups')")
    public @ResponseBody
    Mono<List<String>> getHashesForBackupFiles() {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> fileService.getHashesFor(securityContext.getAuthentication().getPrincipal().toString()));
    }
}
