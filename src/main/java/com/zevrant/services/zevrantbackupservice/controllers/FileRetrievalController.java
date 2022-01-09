package com.zevrant.services.zevrantbackupservice.controllers;

import com.zevrant.services.zevrantbackupservice.services.FileService;
import com.zevrant.services.zevrantbackupservice.services.SecurityContextService;
import com.zevrant.services.zevrantuniversalcommon.rest.backup.response.BackupFilesRetrieval;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Objects;

@RestController
@RequestMapping("/retrieval")
public class FileRetrievalController {

    private final FileService fileService;
    private final SecurityContextService securityContextService;

    @Autowired
    public FileRetrievalController(FileService fileService, SecurityContextService securityContextService) {
        this.fileService = fileService;
        this.securityContextService = securityContextService;
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyAuthority('backups')")
    public Mono<Integer> getImageMediaCount() {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext ->
                        fileService.countHashes(
                                securityContextService.getUsername(
                                        securityContext)
                        )
                );
    }

    @GetMapping("/{page}/{count}")
    @PreAuthorize("hasAnyAuthority('backups')")
    public Mono<BackupFilesRetrieval> retrieveFiles(@PathVariable int page,
                                                    @PathVariable int count,
                                                    @RequestParam("iconWidth") int displayWidth,
                                                    @RequestParam("iconHeight") int displayHeight) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> fileService
                        .getBackupsByPage(
                                securityContextService.getUsername(securityContext),
                                page,
                                count,
                                displayWidth,
                                displayHeight));
    }

    @GetMapping("/{fileHash}")
    @PreAuthorize("hasAnyAuthority('backups')")
    public Mono<ResponseEntity<Resource>> getBackupFile(@PathVariable("fileHash") String fileHash) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> fileService
                        .getBackupFile(
                                securityContextService.getUsername(securityContext),
                                fileHash))
                .map(resource -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; fileName=\""
                                .concat(Objects.requireNonNull(resource.getFilename())).concat("\""))
                        .body(resource));
    }
}

