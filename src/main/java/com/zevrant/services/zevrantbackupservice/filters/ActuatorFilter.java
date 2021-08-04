package com.zevrant.services.zevrantbackupservice.filters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter("/actuator/prometheus")
public class ActuatorFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(ActuatorFilter.class);

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        logger.debug(
                "Starting a transaction for req : {}",
                req.getRequestURI());
        logger.debug("Response Code {}", ((HttpServletResponse) response).getStatus());
        if (((HttpServletResponse) response).getStatus() == 202) { //Status code 202 causes service to show up as down
            ((HttpServletResponse) response).setStatus(200);
        }
        logger.debug("Response Code set to {}", ((HttpServletResponse) response).getStatus());
        chain.doFilter(request, response);
    }
}
