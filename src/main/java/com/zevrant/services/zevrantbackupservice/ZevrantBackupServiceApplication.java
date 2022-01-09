package com.zevrant.services.zevrantbackupservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = {"com.zevrant.services"})
@EnableTransactionManagement
public class ZevrantBackupServiceApplication {
    private static final Logger logger = LoggerFactory.getLogger(ZevrantBackupServiceApplication.class);
    public static void main(String[] args) {
        logger.info("Total memory is: {}GB", Runtime.getRuntime().maxMemory() / Math.Math.pow(10, -9));
        SpringApplication.run(ZevrantBackupServiceApplication.class, args);
    }

}
