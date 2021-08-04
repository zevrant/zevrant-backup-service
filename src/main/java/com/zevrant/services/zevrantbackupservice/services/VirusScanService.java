package com.zevrant.services.zevrantbackupservice.services;

import com.zevrant.services.zevrantbackupservice.exceptions.VirusFoundException;
import fi.solita.clamav.ClamAVClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class VirusScanService {

    private static final Logger logger = LoggerFactory.getLogger(VirusScanService.class);

    private final ClamAVClient clamAVClient;

    public VirusScanService(@Value("${zevrant.services.clamAv.location}") String clamAvLocation,
                            @Value("${zevrant.services.clamAv.port}") int port,
                            @Value("${zevrant.services.clamAv.timeout}") int timeout) throws IOException {
        this.clamAVClient = new ClamAVClient(clamAvLocation, port, timeout);
        assert (clamAVClient.ping());
    }

    public void scanFile(MultipartFile file) throws IOException {
        byte[] reply;
        reply = clamAVClient.scan(file.getBytes());
        if (!ClamAVClient.isCleanReply(reply)) {
            throw new VirusFoundException(file.getOriginalFilename() + " contains a virus!");
        }
    }
}
