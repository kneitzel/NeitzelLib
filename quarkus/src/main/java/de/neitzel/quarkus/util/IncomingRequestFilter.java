package de.neitzel.quarkus.util;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * A JAX-RS filter that logs the details of incoming HTTP requests before they are processed by the application.
 * This filter is annotated with {@code @Provider} to indicate it is a JAX-RS extension,
 * {@code @PreMatching} to specify it should run before the matching phase, and
 * {@code @Slf4j} for automatic logging using Lombok's {@code @Log} annotation.
 * It has a user priority level of {@link Priorities#USER}, which means it is executed after
 * but still within the standard processing chain.
 */
@Provider
@PreMatching
@Slf4j
@Priority(Priorities.USER)
public class IncomingRequestFilter implements ContainerRequestFilter {

    /**
     * Constructs a new {@code IncomingRequestFilter} instance.
     * <p>
     * This is a default, no-argument constructor for the {@link IncomingRequestFilter} class,
     * which provides logging functionality for incoming HTTP requests in a JAX-RS application.
     */
    public IncomingRequestFilter() {
        // Default constructor
    }

    /**
     * Logs the details of incoming HTTP requests before they are processed by the application.
     * This includes logging the request method and URI, as well as all headers present in the request.
     *
     * @param ctx The context of the incoming container request being filtered.
     */
    @Override
    public void filter(ContainerRequestContext ctx) {
        log.info("⬅️  {} {}", ctx.getMethod(), ctx.getUriInfo().getRequestUri());
        ctx.getHeaders().forEach((k, v) -> log.info("Header {}: {}", k, v));
    }
}
