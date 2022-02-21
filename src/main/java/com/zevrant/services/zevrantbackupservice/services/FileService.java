package com.zevrant.services.zevrantbackupservice.services;

import com.zevrant.services.zevrantbackupservice.entities.BackupFile;
import com.zevrant.services.zevrantbackupservice.exceptions.*;
import com.zevrant.services.zevrantbackupservice.repositories.FileRepository;
import com.zevrant.services.zevrantuniversalcommon.rest.backup.request.FileInfo;
import com.zevrant.services.zevrantuniversalcommon.rest.backup.response.BackupFileResponse;
import com.zevrant.services.zevrantuniversalcommon.rest.backup.response.BackupFilesRetrieval;
import com.zevrant.services.zevrantuniversalcommon.services.ChecksumService;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.imageio.ImageIO;
import javax.transaction.Transactional;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.channels.WritableByteChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

@Service

public class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);
    private final String backupDirectory;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final FileRepository fileRepository;
    private final int maxWaitTime;
    private final ChecksumService checksumService;

    @Autowired
    public FileService(FileRepository fileRepository,
                       ThreadPoolTaskExecutor taskExecutor,
                       ChecksumService checksumService,
                       @Value("${zevrant.backup.directory}") String backupDirectory,
                       @Value("${zevrant.backup.io.maxWaitTime}") int maxWaitTime) throws NoSuchAlgorithmException {
        this.fileRepository = fileRepository;
        this.taskExecutor = taskExecutor;
        this.backupDirectory = backupDirectory;
        this.maxWaitTime = maxWaitTime;
        this.checksumService = checksumService;
    }

    public List<FileInfo> filterExisting(List<FileInfo> fileInfos, String user) {
        List<String> hashes = fileInfos.stream().map((FileInfo::getHash)).collect(Collectors.toList());

        logger.debug("{}", hashes);

        List<BackupFile> backupFiles = fileRepository.findHashesByUser(hashes, user);
        List<String> foundHashes = backupFiles.stream().map((BackupFile::getId)).collect(Collectors.toList());
        return fileInfos.stream().filter(fileInfo -> !foundHashes.contains(fileInfo.getHash())).collect(Collectors.toList());
    }

    @Transactional
    public Mono<BackupFileResponse> backupFile(String username, String fileName, Path tempFilePath) {
        String[] filePieces = fileName.split("\\.");
        String fileDirPath = backupDirectory.concat("/").concat(username).concat("/").concat(filePieces[filePieces.length - 1]).concat("/");

        try {
            File fileDir = new File(fileDirPath);
            boolean dirExists = fileDir.exists();
            if (!dirExists && !fileDir.mkdirs()) {
                throw new RuntimeException("Failed to create Directories");
            }
            BackupFile backupFile = new BackupFile();
            String filePath = fileDirPath.concat(fileName);
            final Path outputFilePath = Path.of(filePath);
            final String fileHash = getFileHash(tempFilePath);
            if (fileRepository.existsByIdAndUploadedBy(fileHash, username)) {
                tempFilePath.toFile().delete();
                throw new FileAlreadyExistsException("File with hash ".concat(fileHash).concat(" already exists ").concat("for user ").concat(username));
            }
            Files.move(tempFilePath, outputFilePath);
            backupFile.setFilePath(outputFilePath.toAbsolutePath().toString());
            backupFile.setId(fileHash);
            backupFile.setUploadedBy(username);
            fileRepository.save(backupFile);
            return Mono.just(new BackupFileResponse());
        } catch (Exception ex) {
            logger.error("Failed to write file to disk and database {}, \n {}", ex.getMessage(), ExceptionUtils.getStackTrace(ex));
            return Mono.error(new FailedToBackupFileException("Failed to write file to disk and database "
                    .concat(ex.getMessage()).concat("\n").concat(ExceptionUtils.getStackTrace(ex))));
        }

    }

    public Mono<Void> writeFluxBuffer(Flux<DataBuffer> content, Path filePath) {
        try {

            WritableByteChannel byteChannel = Files.newByteChannel(filePath, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
            return DataBufferUtils.write(content, byteChannel).map(DataBufferUtils::release).then();
        } catch (IOException e) {
            e.printStackTrace();
            return Mono.error(e);
        }
    }

    private String getFileHash(Path fileLocation) throws IOException, NoSuchAlgorithmException {
        BufferedInputStream is = new BufferedInputStream(new FileInputStream(fileLocation.toFile()));
        return checksumService.getSha512Checksum(is);
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
    public String deleteBackupFile(String backedUpFileName, File imageTypeDir) throws IOException, NoSuchAlgorithmException {
        File backedUpFile = new File(imageTypeDir.getAbsolutePath().concat("/").concat(backedUpFileName));
        BufferedInputStream is = new BufferedInputStream(new FileInputStream(backedUpFile));
        String hash = checksumService.getSha512Checksum(is);
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

    public Future<BackupFilesRetrieval> getBackupsByPage(String username, int page, int count, int iconWidth, int iconHeight) {
        CompletableFuture<BackupFilesRetrieval> future = new CompletableFuture<>();
        taskExecutor.execute(() -> {
            Page<BackupFile> backupFiles = fileRepository.findAllByUploadedBy(username, PageRequest.of(page, count));
            int maxItems = backupFiles.getTotalPages() * count;
            List<com.zevrant.services.zevrantuniversalcommon.rest.backup.response.BackupFile> files = backupFiles.stream()
                    .map(backupFile -> {
                        try {

                            com.zevrant.services.zevrantuniversalcommon.rest.backup.response.BackupFile file = new com.zevrant.services.zevrantuniversalcommon.rest.backup.response.BackupFile();
                            String[] filePathPieces = backupFile.getFilePath().split("/");
                            file.setFileName(filePathPieces[filePathPieces.length - 1]);
                            file.setFileHash(backupFile.getId());
                            file.setImageIcon(Base64.getEncoder().encodeToString(scaleImage(backupFile.getFilePath(), iconWidth, iconHeight)));
                            return file;
                        } catch (IOException ex) {
                            future.completeExceptionally(new RuntimeException("Failed to read and create image icon"));
                            return null;
                        }
                    }).collect(Collectors.toList());

            countHashes(username);
            future.complete(new BackupFilesRetrieval(files, maxItems, backupFiles.getNumber(), backupFiles.getTotalPages() - 1));
        });

        return future;
    }

    public byte[] scaleImage(String filePath, int iconWidth, int iconHeight) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        BufferedImage before = ImageIO.read(new File(filePath));
        BufferedImage outputImage = new BufferedImage(iconWidth, iconHeight, BufferedImage.TYPE_INT_RGB);
        assert before != null : "output image null";
        outputImage.getGraphics().drawImage(before.getScaledInstance(iconWidth, iconHeight, Image.SCALE_SMOOTH), 0, 0, null);
        ImageIO.write(outputImage, "jpg", os);
        os.close();
        return os.toByteArray();
    }

    public Future<Resource> getBackupFile(String username, String fileHash, int iconWidth, int iconHeight) {
        CompletableFuture<Resource> future = new CompletableFuture<>();
        taskExecutor.execute(() -> {
            BackupFile backupFile = fileRepository.findBackupFileByIdAndUploadedBy(fileHash, username).orElseThrow(FilesNotFoundException::new);
            Resource resource = null;
            try {
                if (iconHeight == 0 || iconWidth == 0) {
                    resource = new FileSystemResource(backupFile.getFilePath());
                } else {
                    byte[] bytes = scaleImage(backupFile.getFilePath(), iconWidth, iconHeight);
                    resource = new ByteArrayResource(bytes);
                }
                if (resource.exists() && resource.isReadable()) {
                    future.complete(resource);
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                future.completeExceptionally(new RuntimeException("Failed to read resource from disk"));
                return;
            }
            throw new BackupFileNotFoundException("Backup file was found in our system but resulting file data was missing");
        });
        return future;
    }

    public String getFileNameById(String fileHash) {
        Optional<BackupFile> backupFileProxy = fileRepository.findById(fileHash);
        return new File(backupFileProxy.orElseThrow(FilesNotFoundException::new).getFilePath()).getName();
    }
}
