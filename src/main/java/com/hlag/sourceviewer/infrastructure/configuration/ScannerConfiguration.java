package com.hlag.sourceviewer.infrastructure.configuration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ScannerConfiguration {

    private final int scanTimeoutSeconds;
    private final long maxFileSizeBytes;

    @Inject
    public ScannerConfiguration(
            @ConfigProperty(name = "sourceviewer.scan.timeout-seconds", defaultValue = "1800")
            int scanTimeoutSeconds,
            @ConfigProperty(name = "sourceviewer.scan.max-file-size-bytes", defaultValue = "1048576")
            long maxFileSizeBytes) {
        this.scanTimeoutSeconds = scanTimeoutSeconds;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public int scanTimeoutSeconds() {
        return scanTimeoutSeconds;
    }

    public long maxFileSizeBytes() {
        return maxFileSizeBytes;
    }
}
