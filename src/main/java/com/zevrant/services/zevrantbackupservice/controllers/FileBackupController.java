package com.zevrant.services.zevrantbackupservice.controllers;

import com.zevrant.services.zevrantbackupservice.rest.BackupFileRequest;
import com.zevrant.services.zevrantbackupservice.rest.BackupFileResponse;
import com.zevrant.services.zevrantbackupservice.rest.CheckExistence;
import com.zevrant.services.zevrantbackupservice.services.FileService;
import com.zevrant.services.zevrantbackupservice.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@RestController
@RequestMapping("/file-backup")
@PreAuthorize("hasAnyAuthority('backups')")
public class FileBackupController {

    private final FileService fileService;
    private final RestTemplate restTemplate;
    private final UserService userService;

    @Autowired
    public FileBackupController(FileService fileService, RestTemplate restTemplate, UserService userService) {
        this.fileService = fileService;
        this.restTemplate = restTemplate;
        this.userService = userService;
    }

    /**
     * Takes a list of image info and returns those that do not exist
     *
     * @param checkExistence object containing a list of FileInfo
     * @return an object containing a list of FileInfo for files not currently backed up
     */
    @ResponseBody
    @PostMapping("/check-existence")
    public CheckExistence checkIfExists(@RequestHeader("Authorization") String authorization, @RequestBody CheckExistence checkExistence) {
        return new CheckExistence(fileService.filterExisting(checkExistence.getFileInfos(), userService.getUsername(authorization)));
    }

    @ResponseBody
    @PutMapping
    public BackupFileResponse backupFile(@RequestHeader("Authorization") String authorization, @RequestBody BackupFileRequest request) {
        fileService.backupFile(userService.getUsername(authorization), request.getFileInfo(), request.getSerializedFileData());
        return new BackupFileResponse();
    }

    @DeleteMapping
    @Profile(value = {"local", "develop"})
    public @ResponseBody
    List<String> removeStorage(@RequestHeader("Authorization") String authorization) {
        return fileService.removeStorageFor(userService.getUsername(authorization));
    }


}
