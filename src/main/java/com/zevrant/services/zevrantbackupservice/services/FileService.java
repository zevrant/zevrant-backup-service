package com.zevrant.services.zevrantbackupservice.services;

import com.zevrant.services.zevrantbackupservice.entities.BackupFile;
import com.zevrant.services.zevrantbackupservice.exceptions.BackupFileNotFoundException;
import com.zevrant.services.zevrantbackupservice.exceptions.FailedToBackupFileException;
import com.zevrant.services.zevrantbackupservice.exceptions.FailedToDeleteBackupFileException;
import com.zevrant.services.zevrantbackupservice.exceptions.FailedToProcessFileException;
import com.zevrant.services.zevrantbackupservice.repositories.FileRepository;
import com.zevrant.services.zevrantbackupservice.rest.FileInfo;
import net.zevrant.services.security.common.secrets.management.utilities.StringUtilities;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
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
        digest = MessageDigest.getInstance(MessageDigestAlgorithms.SHA_512);
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

    public List<String> removeStorageFor(String username) {
        String directory = backupDirectory.concat("/").concat(username).concat("/");
        logger.info("Listing files in the directory {}", directory);
        File storageDir = new File(directory);
        String[] folders = storageDir.list();
        logger.info("{}", (Object) folders);
        List<String> deletedDigests = new ArrayList<>();
        for (String folder : folders) {
            File imageTypeDir = new File(storageDir.getAbsolutePath().concat("/").concat(folder));
            assert imageTypeDir.isDirectory();
            String[] backedUpFiles = imageTypeDir.list();
            for (String backedUpFileName : backedUpFiles) {
                try {
                    deletedDigests.add(deleteBackupFile(backedUpFileName, imageTypeDir));
                    logger.info("deleted {}", backedUpFileName);
                } catch (FileNotFoundException e) {
                    logger.error("Failed to find backup file backup {}", imageTypeDir.getAbsolutePath().concat("/").concat(backedUpFileName));
                    throw new FailedToDeleteBackupFileException("Failed to find backup file backup ".concat(imageTypeDir.getAbsolutePath().concat("/").concat(backedUpFileName)));
                } catch (IOException e) {
                    logger.error("Failed to process files in storage");
                    throw new FailedToProcessFileException("Failed to process files in storage");
                }
            }
        }
        return deletedDigests;
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


}
