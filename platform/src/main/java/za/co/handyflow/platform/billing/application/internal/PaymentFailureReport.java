package za.co.handyflow.platform.billing.application.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.billing.domain.model.PaymentFailureRules;

import java.time.Instant;

/**
 * REPORT MODE for the payment-failure stages. Once a day it logs, for every business with a failed payment, the stage the
 * owner's rules put it in and what happens today, so the difference can be reviewed before any rollout. It changes nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class PaymentFailureReport {

    private final JdbcTemplate jdbc;

    @Scheduled(cron = "0 50 2 * * *", zone = "Africa/Johannesburg")
    void report() {
        Instant now = Instant.now();
        jdbc.query("SELECT tenant_id, status, past_due_since FROM subscriptions WHERE past_due_since IS NOT NULL AND status IN ('PAST_DUE', 'SUSPENDED')",
                rs -> {
                    var stage = PaymentFailureRules.stage(rs.getTimestamp("past_due_since").toInstant(), now);
                    String today = "PAST_DUE".equals(rs.getString("status")) ? "full access until the 7-day grace ends, then fully suspended" : "fully suspended (every module answers 402)";
                    log.warn("PAYMENT-FAILURE-REPORT tenant={} status={} rulesStage={} daysLeftInStage={} today=\"{}\" (report mode, nothing changed)",
                            rs.getObject("tenant_id"), rs.getString("status"), stage.access(), stage.daysLeftInStage(), today);
                });
    }
}
