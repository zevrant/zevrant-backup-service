package com.zevrant.services.zevrantbackupservice.services;

import com.zevrant.services.zevrantbackupservice.entities.BackupFile;
import com.zevrant.services.zevrantbackupservice.exceptions.*;
import com.zevrant.services.zevrantbackupservice.repositories.FileRepository;
import com.zevrant.services.zevrantsecuritycommon.utilities.StringUtilities;
import com.zevrant.services.zevrantuniversalcommon.rest.backup.request.FileInfo;
import com.zevrant.services.zevrantuniversalcommon.rest.backup.response.BackupFilesRetrieval;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service

public class FileService {

    private final MessageDigest digest;
    private static final Logger logger = LoggerFactory.getLogger(FileService.class);
    private final String backupDirectory;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final FileRepository fileRepository;
    private final int maxWaitTime;

    @Autowired
    public FileService(FileRepository fileRepository,
                       ThreadPoolTaskExecutor taskExecutor,
                       @Value("${zevrant.backup.directory}") String backupDirectory,
                       @Value("${zevrant.backup.io.maxWaitTime}") int maxWaitTime) throws NoSuchAlgorithmException {
        this.fileRepository = fileRepository;
        this.taskExecutor = taskExecutor;
        this.backupDirectory = backupDirectory;
        this.maxWaitTime = maxWaitTime;
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

    private String writeFileToDisk(String filePath, String serializedFileData) throws IOException, ExecutionException, InterruptedException, TimeoutException {
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
        final CompletableFuture<Boolean> isCompelte = new CompletableFuture<>();
        taskExecutor.execute(() -> {
            LocalDateTime future = LocalDateTime.now().plusMinutes(maxWaitTime);
            File finalFile = new File(filePath);
            boolean incorrectFileSize = finalFile.length() != serializedFileData.getBytes(StandardCharsets.UTF_8).length;
            boolean hasTimeoutOccured = LocalDateTime.now().isBefore(future);
            while (incorrectFileSize && hasTimeoutOccured) {
                Thread.onSpinWait();
                incorrectFileSize = finalFile.length() != serializedFileData.getBytes(StandardCharsets.UTF_8).length;
            }
            isCompelte.complete(finalFile.length() == serializedFileData.getBytes(StandardCharsets.UTF_8).length);
        });

        //add some extra time to allow for the timout in the thread to execute normally
        if (!isCompelte.get(maxWaitTime + 1, TimeUnit.MINUTES)) {
            throw new FailedToBackupFileException("File was not written to disk in the specified timout period");
        }

        return file.getAbsolutePath();
    }

    @Transactional
    public List<String> removeStorageFor(String username) {
        String directory = backupDirectory.concat("/").concat(username).concat("/");
        logger.info("Listing files in the directory {}", directory);
        File storageDir = new File(directory);
        List<BackupFile> deletedFiles = fileRepository.deleteBackupFileByUploadedBy(username);
        boolean fileSystem = !storageDir.exists()
                || !deleteRecursively(storageDir.getAbsolutePath(), username);
        if (deletedFiles.isEmpty() || fileSystem) {
            logger.info("No file found for {} in file system {}, database {}",
                    username, fileSystem, deletedFiles.isEmpty());
            throw new FilesNotFoundException();
        }
        return deletedFiles.stream().map(BackupFile::getId).collect(Collectors.toList());
    }

    private boolean deleteRecursively(String path, String username) {
        File file = new File(path);
        final boolean[] deleted = new boolean[]{file.exists()};
        if (deleted[0]) {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                Arrays.stream((files == null) ? Collections.emptyList().toArray() : files)
                        .forEach(listedFile -> {
                            if (!((File) listedFile).getAbsolutePath().equals(file.getAbsolutePath())) {
                                deleted[0] = deleted[0] && deleteRecursively(((File) listedFile).getAbsolutePath(), username);
                            }
                        });
            } else {
                boolean isDeleted = file.delete();
                logger.debug("file {} deleted? {}", file.getAbsoluteFile(), isDeleted);
                deleted[0] = isDeleted && deleted[0];
            }
        }

        return deleted[0];
    }

    @Transactional(rollbackOn = IOException.class)
    public String deleteBackupFile(String backedUpFileName, File imageTypeDir) throws IOException {
        File backedUpFile = new File(imageTypeDir.getAbsolutePath().concat("/").concat(backedUpFileName));
        BufferedInputStream is = new BufferedInputStream(new FileInputStream(backedUpFile));
        String hash = StringUtilities.getChecksum(digest, is);
        Optional<BackupFile> file = fileRepository.findById(hash);
        fileRepository.delete(file.orElseThrow(() -> {
            logger.error("Failed to find file with hash {} having filename {} and imageTypeDir {}",
                    hash, backedUpFileName, imageTypeDir.getName());
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

    public int countHashes(String username) {
        return fileRepository.countAllByUploadedBy(username);
    }

    public BackupFilesRetrieval getBackupsByPage(String username, int page, int count) {
        Page<BackupFile> backupFiles = fileRepository.findAllByUploadedBy(username, PageRequest.of(page, count));

        List<com.zevrant.services.zevrantuniversalcommon.rest.backup.response.BackupFile> files = backupFiles.stream()
                .map(backupFile -> {
                    com.zevrant.services.zevrantuniversalcommon.rest.backup.response.BackupFile file = new com.zevrant.services.zevrantuniversalcommon.rest.backup.response.BackupFile();
                    String[] filePathPieces = backupFile.getFilePath().split("/");
                    file.setFileName(filePathPieces[filePathPieces.length - 1]);
                    try (BufferedReader reader = new BufferedReader(new FileReader(backupFile.getFilePath()))) {
                        StringBuilder imageBuilder = new StringBuilder();
                        reader.lines().forEach(imageBuilder::append);
                        file.setImageData(imageBuilder.toString());
                    } catch (IOException ex) {
                        logger.error("failed to retrieve file with name {}", file.getFileName());
                    }
                    return file;
                }).collect(Collectors.toList());

        int maxItems = countHashes(username);
        return new BackupFilesRetrieval(files, maxItems, backupFiles.getNumber(), backupFiles.getTotalPages() - 1);
    }
}
