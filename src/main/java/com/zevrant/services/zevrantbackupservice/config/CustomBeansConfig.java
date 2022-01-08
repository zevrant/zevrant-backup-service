package com.zevrant.services.zevrantbackupservice.config;

import com.zevrant.services.zevrantuniversalcommon.services.ChecksumService;
import com.zevrant.services.zevrantuniversalcommon.services.HexConversionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomBeansConfig {

    @Bean
    public HexConversionService configureHexConversionService() {
        return new HexConversionService();
    }

    @Bean
    public ChecksumService configureChecksumService(HexConversionService hexConversionService) {
        return new ChecksumService(hexConversionService);
    }
}
