package com.zevrant.services.zevrantbackupservice.services;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class SecurityContextService {

    public String getUsername(SecurityContext securityContext) {
        return ((Jwt) securityContext.getAuthentication().getPrincipal())
                .getClaim("preferred_username");
    }
}
