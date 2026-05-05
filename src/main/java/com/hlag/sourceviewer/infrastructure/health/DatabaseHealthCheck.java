package com.hlag.sourceviewer.infrastructure.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Readiness health check for the database connection.
 * Accessible at /health/ready.
 */
@Readiness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {

    private final DataSource dataSource;

    @Inject
    public DatabaseHealthCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public HealthCheckResponse call() {
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(2);
            return HealthCheckResponse.named("database")
                    .status(valid)
                    .withData("url", connection.getMetaData().getURL())
                    .build();
        } catch (Exception exception) {
            return HealthCheckResponse.named("database")
                    .down()
                    .withData("error", exception.getMessage())
                    .build();
        }
    }
}
