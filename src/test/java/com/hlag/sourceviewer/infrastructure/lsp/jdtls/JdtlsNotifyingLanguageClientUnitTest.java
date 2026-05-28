package com.hlag.sourceviewer.infrastructure.lsp.jdtls;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdtlsNotifyingLanguageClientUnitTest {

    private JdtlsNotifyingLanguageClient client;

    @BeforeEach
    void setUp() {
        client = new JdtlsNotifyingLanguageClient();
    }

    @Test
    void service_ready_future_completes_when_service_ready_message_received() throws Exception {
        client.languageStatus(new JdtlsStatusReport("Started", "ServiceReady"));

        assertThat(client.serviceReadyFuture().get(1, TimeUnit.SECONDS)).isNull();
        assertThat(client.serviceReadyFuture().isDone()).isTrue();
        assertThat(client.serviceReadyFuture().isCompletedExceptionally()).isFalse();
    }

    @Test
    void service_ready_future_is_not_completed_during_indexing_progress() {
        client.languageStatus(new JdtlsStatusReport("Starting", "Indexing 10%"));
        client.languageStatus(new JdtlsStatusReport("Starting", "Indexing 80%"));

        assertThat(client.serviceReadyFuture().isDone()).isFalse();
    }

    @Test
    void service_ready_future_completes_exceptionally_when_error_status_received() {
        client.languageStatus(new JdtlsStatusReport("Error", "failed to initialize"));

        assertThat(client.serviceReadyFuture().isCompletedExceptionally()).isTrue();
    }

    @Test
    void service_ready_future_is_not_completed_again_after_first_completion() throws Exception {
        client.languageStatus(new JdtlsStatusReport("Started", "ServiceReady"));
        client.languageStatus(new JdtlsStatusReport("Started", "ServiceReady")); // duplicate

        assertThat(client.serviceReadyFuture().get(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void error_after_service_ready_does_not_change_future_state() throws Exception {
        client.languageStatus(new JdtlsStatusReport("Started", "ServiceReady"));
        client.languageStatus(new JdtlsStatusReport("Error", "late error"));

        // The future was already complete normally — late error is ignored
        assertThat(client.serviceReadyFuture().isCompletedExceptionally()).isFalse();
        assertThat(client.serviceReadyFuture().get(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void null_report_is_ignored_gracefully() {
        client.languageStatus(null);

        assertThat(client.serviceReadyFuture().isDone()).isFalse();
    }
}

