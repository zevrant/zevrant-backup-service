package com.zevrant.services.zevrantbackupservice.controllers;

import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.zevrant.services.zevrantbackupservice.rest.UpdateCheckResponse;
import com.zevrant.services.zevrantbackupservice.services.UpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping("/updates")
public class UpdateController {

    private static final Logger logger = LoggerFactory.getLogger(UpdateController.class);

    private final UpdateService updateService;

    @Autowired
    public UpdateController(UpdateService updateService) {
        this.updateService = updateService;
    }

    @GetMapping
    public UpdateCheckResponse checkForUpdates(@RequestParam String version) {
        return updateService.checkForUpdate(version);
    }

    @ResponseBody
    @GetMapping("/download")
    public byte[] downloadUpdate(@RequestParam String version, HttpServletResponse response) throws IOException {
        response.addHeader("Content-Type", "appplication/octet-stream");
        S3ObjectInputStream is = updateService.getApk(version);

        logger.debug("Successfully retrieved input stream, {} bytes are available", is.available());
//        int byteCount = 0;
//        byte[] bytes = new byte[1024];
//        int bytesRead = is.read(bytes);
//        while(bytesRead >= 0) {
//            byteCount += bytesRead;
//            response.getOutputStream().write(bytes);
//            bytes = new byte[1024];
//            bytesRead = is.read(bytes);
//        }
        byte[] bytes = is.readAllBytes();
        logger.debug("finished writing data, bytes written {}", bytes.length);
        return bytes;
    }
}
