package de.neitzel.quarkus.util;

import io.quarkus.vertx.web.RouteFilter;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

/**
 * A utility class that logs details of all incoming routes within a Quarkus application using Vert.x Web.
 * This logging is performed at the global level, meaning it applies to all routes handled by the application.
 */
@Slf4j
public class GlobalRouteLogging {

    /**
     * Logs all details of the incoming route request, including the method and URI, as well as all headers present in the request.
     * This method is designed to be used within a Vert.x Web application to log details for every incoming route globally.
     *
     * @param rc The routing context containing information about the current request and response.
     */
    @RouteFilter(100)
    void logAll(RoutingContext rc) {
        log.info("⬅️  {} {}", rc.request().method(), rc.request().uri());
        rc.request().headers().forEach(h -> log.info("Header {}: {}", h.getKey(), h.getValue()));
        rc.next(); // wichtig: weiterreichen
    }
}
