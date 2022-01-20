package com.zevrant.services.zevrantbackupservice.controllers;

import com.zevrant.services.zevrantbackupservice.exceptions.BackupFileNotFoundException;
import com.zevrant.services.zevrantbackupservice.services.FileService;
import com.zevrant.services.zevrantbackupservice.services.SecurityContextService;
import com.zevrant.services.zevrantuniversalcommon.rest.backup.request.CheckExistence;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;

@RestController
@RequestMapping("/file-backup")

public class FileBackupController {

    private static final Logger logger = LoggerFactory.getLogger(FileBackupController.class);
    private final FileService fileService;
    private final List<String> activeProfiles;
    private final SecurityContextService securityContextService;

    private final Function<? super byte[], Void> writeToStream = (byte[] bytes) -> {
        try (BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(new File("/storage/backups/userdata/zevrant/test.jpg")))) {
            outputStream.write(bytes);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    };

    @Autowired
    public FileBackupController(FileService fileService,
                                SecurityContextService securityContextService,
                                @Value("${spring.profiles.active}") String activeProfiles) {
        this.fileService = fileService;
        this.securityContextService = securityContextService;
        this.activeProfiles = Arrays.asList(activeProfiles.split(","));
    }

    /**
     * Takes a list of image info and returns those that do not exist
     *
     * @param checkExistence object containing a list of FileInfo
     * @return an object containing a list of FileInfo for files not currently backed up
     */
    @PostMapping("/check-existence")
    @PreAuthorize("hasAuthority('backups')")
    public Mono<CheckExistence> checkIfExists(@RequestBody CheckExistence checkExistence) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> new CheckExistence(
                        fileService.filterExisting(checkExistence.getFileInfos(),
                                securityContextService.getUsername(securityContext))));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('backups')")
    public Mono<Void> backupFile(@RequestPart("file") Mono<FilePart> filePartFlux,
                                 @RequestPart("fileName") String fileName) {

        Path tempFilePath = Path.of("/tmp/".concat(UUID.randomUUID().toString()));
        Flux<DataBuffer> dataBuffers = filePartFlux.flatMapMany(Part::content);
        return fileService.writeFluxBuffer(dataBuffers, tempFilePath)
                .and(ReactiveSecurityContextHolder.getContext()
                        .publishOn(Schedulers.boundedElastic())
                        .map(securityContext ->
                                fileService.backupFile(
                                        securityContextService.getUsername(securityContext),
                                        fileName,
                                        tempFilePath
                                )
                        )).then();
    }

    @DeleteMapping
    @PreAuthorize("hasAuthority('backups')")
    public Mono<List<String>> removeStorage(@RequestBody(required = false) CheckExistence request) {
        return ReactiveSecurityContextHolder.getContext()
                .publishOn(Schedulers.boundedElastic())
                .map(securityContext -> {
                    String username = securityContextService.getUsername(securityContext);
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
                                } catch (NoSuchAlgorithmException e) {
                                    e.printStackTrace();
                                    logger.error("Failed to find algorithm to create hash with");
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
                .map(securityContext ->
                        fileService.getHashesFor(
                                securityContextService.getUsername(securityContext)
                        )
                );
    }


}
