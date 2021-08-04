package com.zevrant.services.zevrantbackupservice.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

@Configuration
public class RestTemplateConfig extends net.zevrant.services.security.common.secrets.management.config.RestTemplateConfig {

    @Bean
    public RestTemplate configure(RestTemplateBuilder restTemplateBuilder) throws KeyManagementException, NoSuchAlgorithmException {
        return super.configure(restTemplateBuilder);
    }
}
