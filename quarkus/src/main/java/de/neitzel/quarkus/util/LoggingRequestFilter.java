package de.neitzel.quarkus.util;

import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * A client-side filter that logs the details of HTTP requests before they are sent.
 */
@Slf4j
@Provider
public class LoggingRequestFilter implements ClientRequestFilter {

    /**
     * Constructs a new {@code LoggingRequestFilter} instance.
     * <p>
     * This is a default, no-argument constructor for the {@link LoggingRequestFilter} class,
     * which provides logging functionality for outgoing HTTP requests in a JAX-RS client application.
     */
    public LoggingRequestFilter() {
        // Default constructor
    }
    
    /**
     * Logs the details of HTTP requests before they are sent.
     *
     * @param requestContext The context of the client request being filtered.
     */
    @Override
    public void filter(ClientRequestContext requestContext) {
        log.info("➡️  Calling {} {}", requestContext.getMethod(), requestContext.getUri());
        requestContext.getHeaders().forEach((k, v) -> log.info("Header {}: {}", k, v));
    }
}
