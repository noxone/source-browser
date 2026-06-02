package com.hlag.sourceviewer.infrastructure.lsp.jdtls;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressNotification;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JdtlsNotifyingLanguageClientUnitTest {

    private JdtlsNotifyingLanguageClient client;

    @BeforeEach
    void setUp() {
        client = new JdtlsNotifyingLanguageClient();
    }

    // ── serviceReadyFuture ────────────────────────────────────────────────────

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

    // ── work-done-progress tracking ───────────────────────────────────────────

    @Nested
    class WorkDoneProgressTracking {

        @Test
        void active_count_is_zero_before_any_progress() {
            assertThat(client.activeWorkDoneProgressCount()).isZero();
            assertThat(client.activeWorkDoneProgressSnapshot()).isEmpty();
        }

        @Test
        void begin_increments_active_count() {
            notifyProgress("token-1", begin("Building workspace"));

            assertThat(client.activeWorkDoneProgressCount()).isEqualTo(1);
            assertThat(client.activeWorkDoneProgressSnapshot()).containsKey("token-1");
            assertThat(client.activeWorkDoneProgressSnapshot()).containsValue("Building workspace");
        }

        @Test
        void end_decrements_active_count() {
            notifyProgress("token-1", begin("Building workspace"));
            notifyProgress("token-1", end("Done"));

            assertThat(client.activeWorkDoneProgressCount()).isZero();
        }

        @Test
        void multiple_concurrent_tokens_are_tracked_independently() {
            notifyProgress("token-a", begin("Building workspace"));
            notifyProgress("token-b", begin("Resolving dependencies"));

            assertThat(client.activeWorkDoneProgressCount()).isEqualTo(2);

            notifyProgress("token-a", end(null));

            assertThat(client.activeWorkDoneProgressCount()).isEqualTo(1);
            assertThat(client.activeWorkDoneProgressSnapshot()).containsKey("token-b");
            assertThat(client.activeWorkDoneProgressSnapshot()).doesNotContainKey("token-a");
        }

        @Test
        void report_does_not_change_active_count() {
            notifyProgress("token-1", begin("Building workspace"));
            notifyProgress("token-1", report("50% done"));

            assertThat(client.activeWorkDoneProgressCount()).isEqualTo(1);
        }

        @Test
        void snapshot_is_unmodifiable() {
            notifyProgress("token-1", begin("Building workspace"));

            assertThat(client.activeWorkDoneProgressSnapshot())
                    .isUnmodifiable();
        }

        @Test
        void end_for_unknown_token_is_handled_gracefully() {
            notifyProgress("phantom-token", end("Done"));

            assertThat(client.activeWorkDoneProgressCount()).isZero();
        }

        private void notifyProgress(String tokenString, WorkDoneProgressNotification notification) {
            ProgressParams params = new ProgressParams(
                    Either.forLeft(tokenString),
                    Either.forLeft(notification));
            client.notifyProgress(params);
        }

        private static WorkDoneProgressBegin begin(String title) {
            WorkDoneProgressBegin begin = new WorkDoneProgressBegin();
            begin.setTitle(title);
            return begin;
        }

        private static WorkDoneProgressReport report(String message) {
            WorkDoneProgressReport report = new WorkDoneProgressReport();
            report.setMessage(message);
            return report;
        }

        private static WorkDoneProgressEnd end(String message) {
            WorkDoneProgressEnd end = new WorkDoneProgressEnd();
            end.setMessage(message);
            return end;
        }
    }
}