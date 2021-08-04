package com.zevrant.services.zevrantbackupservice.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class UserService {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    @Autowired
    public UserService(RestTemplate restTemplate, @Value("${zevrant.services.proxy.baseUrl}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public String getUsername(String token) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("Authorization", token);
        HttpEntity<String> entity = new HttpEntity<>("", httpHeaders);
        ResponseEntity<String> response = restTemplate.exchange(baseUrl.concat("/zevrant-oauth2-service/user/username"), HttpMethod.GET, entity, String.class);
        assert (response.getStatusCode().is2xxSuccessful());
        assert (response.getBody() != null);
        return response.getBody().split("\"")[3];
    }
}
