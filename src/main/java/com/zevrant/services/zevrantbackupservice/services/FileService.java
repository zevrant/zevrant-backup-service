package com.zevrant.services.zevrantbackupservice.services;

import com.zevrant.services.zevrantbackupservice.entities.File;
import com.zevrant.services.zevrantbackupservice.exceptions.FailedToBackupFileException;
import com.zevrant.services.zevrantbackupservice.repositories.FileRepository;
import com.zevrant.services.zevrantbackupservice.rest.FileInfo;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service

public class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);
    private final String backupDirectory;

    private final FileRepository fileRepository;

    @Autowired
    public FileService(FileRepository fileRepository,
                       @Value("${zevrant.backup.directory}") String backupDirectory) {
        this.fileRepository = fileRepository;
        this.backupDirectory = backupDirectory;
    }

    public List<FileInfo> filterExisting(List<FileInfo> fileInfos, String user) {
        List<String> hashes = fileInfos.stream().map((FileInfo::getHash)).collect(Collectors.toList());

        logger.debug("{}", hashes);

        List<File> files = fileRepository.findHashesByUser(hashes, user);
        List<String> foundHashes = files.stream().map((File::getId)).collect(Collectors.toList());
        return fileInfos.stream().filter(fileInfo -> !foundHashes.contains(fileInfo.getHash())).collect(Collectors.toList());
    }

    @Transactional
    public void backupFile(String username, FileInfo fileInfo, String serializedFileData) {
        String[] filePieces = fileInfo.getFileName().split("\\.");
        String fileDirPath = backupDirectory.concat("/").concat(username).concat("/").concat(filePieces[filePieces.length - 1]).concat("/");
        try {
            java.io.File fileDir = new java.io.File(fileDirPath);
            boolean dirExists = fileDir.exists();
            if (!dirExists && !fileDir.mkdirs()) {
                throw new RuntimeException("Failed to create Directories");
            }
            File file = new File();
            String filePath = fileDirPath.concat(fileInfo.getFileName());
            filePath = writeFileToDisk(filePath, serializedFileData);
            file.setFilePath(filePath);
            file.setId(fileInfo.getHash());
            file.setUploadedBy(username);
            fileRepository.save(file);
        } catch (Exception ex) {
            logger.error("Failed to write file to disk and database {}, \n {}", ex.getMessage(), ExceptionUtils.getStackTrace(ex));
            throw new FailedToBackupFileException("Failed to write file to disk and database "
                    .concat(ex.getMessage()).concat("\n").concat(ExceptionUtils.getStackTrace(ex)));
        }
    }

    private String writeFileToDisk(String filePath, String serializedFileData) throws IOException {
        java.io.File file = new java.io.File(filePath);
        if (file.exists()) {
            int i = 0;
            do {
                file = new java.io.File(filePath.concat(" (".concat(String.valueOf(i).concat(")"))));
                i++;
            } while (!file.exists());

        }
        if (!file.createNewFile()) {
            throw new RuntimeException(("Failed to create new File"));
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(serializedFileData);
        writer.flush();
        return file.getAbsolutePath();
    }
}
