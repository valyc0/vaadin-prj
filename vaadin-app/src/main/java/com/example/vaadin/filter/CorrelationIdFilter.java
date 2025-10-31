package com.example.vaadin.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet Filter that generates or propagates a correlation ID for each HTTP request.
 * The correlation ID is stored in MDC and can be used throughout the entire transaction,
 * including reactive WebClient calls.
 * 
 * Order(1) ensures this filter runs early in the filter chain.
 */
@Component
@Order(1)
public class CorrelationIdFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(CorrelationIdFilter.class);
    private static final String CORRELATION_ID_KEY = "correlationId";
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        try {
            // Try to get correlation ID from incoming request header, or generate new one
            String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isEmpty()) {
                correlationId = UUID.randomUUID().toString();
            }
            
            // Store correlation ID in MDC for the entire request lifecycle
            MDC.put(CORRELATION_ID_KEY, correlationId);
            
            // Add correlation ID to response header for downstream tracing
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);
            
            logger.debug("╔════════════════════════════════════════════════════════════════");
            logger.debug("║ HTTP REQUEST STARTED [{}]", correlationId);
            logger.debug("║ Method: {} {}", httpRequest.getMethod(), httpRequest.getRequestURI());
            logger.debug("╚════════════════════════════════════════════════════════════════");
            
            // Continue the filter chain
            chain.doFilter(request, response);
            
            logger.debug("╔════════════════════════════════════════════════════════════════");
            logger.debug("║ HTTP REQUEST COMPLETED [{}]", correlationId);
            logger.debug("║ Status: {}", httpResponse.getStatus());
            logger.debug("╚════════════════════════════════════════════════════════════════");
            
        } finally {
            // Always clean up MDC to prevent memory leaks
            MDC.remove(CORRELATION_ID_KEY);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("CorrelationIdFilter initialized");
    }

    @Override
    public void destroy() {
        logger.info("CorrelationIdFilter destroyed");
    }
}
