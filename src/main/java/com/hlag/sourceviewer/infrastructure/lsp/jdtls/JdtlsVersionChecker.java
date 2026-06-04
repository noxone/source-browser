package com.hlag.sourceviewer.infrastructure.lsp.jdtls;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerInfo;

import java.util.logging.Logger;
import java.util.logging.Level;

public class JdtlsVersionChecker {

    private static final Logger LOG = Logger.getLogger(JdtlsVersionChecker.class.getName());

    private static final int REQUIRED_MAJOR = 1;
    private static final int REQUIRED_MINOR = 59;

    /**
     * Checks the jdtls version after initialize() and logs a warning if it's too old.
     *
     * @param result the InitializeResult returned by languageServer.initialize(...).get()
     */
    public static void checkVersion(InitializeResult result) {
        ServerInfo serverInfo = result.getServerInfo();

        if (serverInfo == null) {
            LOG.warning("jdtls did not return server info – version check skipped. " +
                        "Consider upgrading to jdtls >= " + REQUIRED_MAJOR + "." + REQUIRED_MINOR);
            return;
        }

        String versionString = serverInfo.getVersion();
        if (versionString == null || versionString.isBlank()) {
            LOG.warning("jdtls returned no version string – version check skipped.");
            return;
        }

        if (!isSufficientVersion(versionString)) {
            LOG.warning(String.format(
                "jdtls version %s is below the required minimum %d.%d. " +
                "Some features may not work correctly. Please upgrade jdtls.",
                versionString, REQUIRED_MAJOR, REQUIRED_MINOR
            ));
        } else {
            LOG.info("jdtls version " + versionString + " meets the minimum requirement.");
        }
    }

    /**
     * Parses the version string and checks major.minor against the required minimum.
     * jdtls version strings look like: "1.31.0.202312211634"
     */
    static boolean isSufficientVersion(String versionString) {
        // Strip any leading non-numeric prefix (e.g. "v1.31.0")
        String normalized = versionString.replaceFirst("^[^0-9]*", "");
        String[] parts = normalized.split("[.\\-]");

        try {
            int major = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

            if (major != REQUIRED_MAJOR) {
                return major > REQUIRED_MAJOR;
            }
            return minor >= REQUIRED_MINOR;

        } catch (NumberFormatException e) {
            LOG.log(Level.WARNING, "Could not parse jdtls version string: " + versionString, e);
            return false; // treat unparseable version as insufficient
        }
    }
}