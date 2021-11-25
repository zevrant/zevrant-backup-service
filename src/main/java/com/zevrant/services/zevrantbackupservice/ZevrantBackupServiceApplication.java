package com.zevrant.services.zevrantbackupservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication(scanBasePackages = {"com.zevrant.services"})
@EnableTransactionManagement
public class ZevrantBackupServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZevrantBackupServiceApplication.class, args);
    }

}
