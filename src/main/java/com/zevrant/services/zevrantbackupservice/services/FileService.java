package com.zevrant.services.zevrantbackupservice.services;

import com.zevrant.services.zevrantbackupservice.entities.BackupFile;
import com.zevrant.services.zevrantbackupservice.exceptions.*;
import com.zevrant.services.zevrantbackupservice.repositories.FileRepository;
import com.zevrant.services.zevrantbackupservice.rest.FileInfo;
import com.zevrant.services.zevrantsecuritycommon.utilities.StringUtilities;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import javax.transaction.Transactional;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service

public class FileService {

    private final MessageDigest digest;
    private static final Logger logger = LoggerFactory.getLogger(FileService.class);
    private final String backupDirectory;

    private final FileRepository fileRepository;

    @Autowired
    public FileService(FileRepository fileRepository,
                       @Value("${zevrant.backup.directory}") String backupDirectory) throws NoSuchAlgorithmException {
        this.fileRepository = fileRepository;
        this.backupDirectory = backupDirectory;
        digest = MessageDigest.getInstance("SHA-512");
    }

    public List<FileInfo> filterExisting(List<FileInfo> fileInfos, String user) {
        List<String> hashes = fileInfos.stream().map((FileInfo::getHash)).collect(Collectors.toList());

        logger.debug("{}", hashes);

        List<BackupFile> backupFiles = fileRepository.findHashesByUser(hashes, user);
        List<String> foundHashes = backupFiles.stream().map((BackupFile::getId)).collect(Collectors.toList());
        return fileInfos.stream().filter(fileInfo -> !foundHashes.contains(fileInfo.getHash())).collect(Collectors.toList());
    }

    @Transactional
    public void backupFile(String username, FileInfo fileInfo, String serializedFileData) {
        String[] filePieces = fileInfo.getFileName().split("\\.");
        String fileDirPath = backupDirectory.concat("/").concat(username).concat("/").concat(filePieces[filePieces.length - 1]).concat("/");
        try {
            File fileDir = new File(fileDirPath);
            boolean dirExists = fileDir.exists();
            if (!dirExists && !fileDir.mkdirs()) {
                throw new RuntimeException("Failed to create Directories");
            }
            BackupFile backupFile = new BackupFile();
            String filePath = fileDirPath.concat(fileInfo.getFileName());
            filePath = writeFileToDisk(filePath, serializedFileData);
            backupFile.setFilePath(filePath);
            backupFile.setId(fileInfo.getHash());
            backupFile.setUploadedBy(username);
            fileRepository.save(backupFile);
        } catch (Exception ex) {
            logger.error("Failed to write file to disk and database {}, \n {}", ex.getMessage(), ExceptionUtils.getStackTrace(ex));
            throw new FailedToBackupFileException("Failed to write file to disk and database "
                    .concat(ex.getMessage()).concat("\n").concat(ExceptionUtils.getStackTrace(ex)));
        }
    }

    private String writeFileToDisk(String filePath, String serializedFileData) throws IOException {
        File file = new File(filePath);
        if (file.exists()) {
            int i = 0;
            do {
                file = new File(filePath.concat(" (".concat(String.valueOf(i).concat(")"))));
                i++;
            } while (file.exists());

        }
        if (!file.createNewFile()) {
            throw new RuntimeException(("Failed to create new File"));
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(serializedFileData);
        writer.flush();
        return file.getAbsolutePath();
    }

    @Transactional
    public List<String> removeStorageFor(String username) {
        String directory = backupDirectory.concat("/").concat(username).concat("/");
        logger.info("Listing files in the directory {}", directory);
        File storageDir = new File(directory);
        List<BackupFile> deletedFiles = fileRepository.deleteBackupFileByUploadedBy(username);
        if (deletedFiles.isEmpty() || !storageDir.exists() || !FileSystemUtils.deleteRecursively(storageDir)) {
            logger.info("No files found for user {}", username);
            throw new FilesNotFoundException();
        }
        return deletedFiles.stream().map(BackupFile::getId).collect(Collectors.toList());
    }

    @Transactional(rollbackOn = IOException.class)
    public String deleteBackupFile(String backedUpFileName, File imageTypeDir) throws IOException {
        File backedUpFile = new File(imageTypeDir.getAbsolutePath().concat("/").concat(backedUpFileName));
        BufferedInputStream is = new BufferedInputStream(new FileInputStream(backedUpFile));
        String hash = StringUtilities.getChecksum(digest, is);
        Optional<BackupFile> file = fileRepository.findById(hash);
        fileRepository.delete(file.orElseThrow(() -> {
            logger.error("Failed to find file with hash {} having filename {} and imageTypeDir {}", hash, backedUpFileName, imageTypeDir.getName());
            return new BackupFileNotFoundException("Failed to find file with hash ".concat(hash));
        }));
        return hash;
    }

    public File getImageTypeDir(String imageType, String username) {
        //TODO add validation
        if (StringUtils.isNotBlank(imageType)
                && imageType.contains("..")) {
            throw new InvalidFormatException("Invalid format detected");
        }
        File imageTypeDir = new File(backupDirectory.concat("/").concat(username).concat("/").concat(imageType));
        if (imageTypeDir.exists()) {
            return imageTypeDir;
        }
        throw new FailedToDeleteBackupFileException("Data Not Found");
    }

    public List<String> getHashesFor(String username) {
        return fileRepository.findAllByUploadedBy(username)
                .stream()
                .map(BackupFile::getId)
                .collect(Collectors.toList());
    }
}
