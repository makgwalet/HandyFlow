package za.co.handyflow.platform.shared;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Replaces the EventPublicationRegistry-based version of this class —
 * that version failed to compile with "Cannot resolve symbol
 * 'EventPublicationRegistry'", which most likely means
 * spring-modulith-starter-jpa (the artifact that provides both that
 * interface AND creates the event_publication table in the first place)
 * was never actually added as a dependency — only
 * spring-modulith-starter-test was confirmed added, back in Phase 0.
 * <p>
 * ACTION NEEDED BEFORE THIS CLASS CAN WORK EITHER:
 * Check pom.xml for spring-modulith-starter-jpa. If it's missing, add it:
 * <pre>
 *   &lt;dependency&gt;
 *       &lt;groupId&gt;org.springframework.modulith&lt;/groupId&gt;
 *       &lt;artifactId&gt;spring-modulith-starter-jpa&lt;/artifactId&gt;
 *   &lt;/dependency&gt;
 * </pre>
 * (version resolved via the same spring-modulith-bom already used for
 * spring-modulith-starter-test — see Phase0-Setup-Instructions.md from
 * earlier in this engagement). Without this dependency, event_publication
 * doesn't exist as a table at all, DomainEvent's Javadoc claim about it
 * was aspirational rather than actually wired up, and THIS class's query
 * below will fail with "relation event_publication does not exist" —
 * that failure, if you see it, confirms the missing-dependency theory
 * rather than indicating a bug in this class.
 * <p>
 * WHY JDBC INSTEAD OF FIXING THE EventPublicationRegistry VERSION: that
 * version depends on the exact Java API shape of whatever Modulith
 * version ends up pinned, which I already flagged once as unverified and
 * which then genuinely failed to resolve. Querying the table directly
 * sidesteps the question entirely — event_publication's column layout
 * (id, listener_id, event_type, serialized_event, publication_date,
 * completion_date) has been stable across Modulith versions, and
 * JdbcTemplate is already a proven, available dependency throughout this
 * codebase (ScmService, DeskService, EventNumberGenerator, and others all
 * use it directly). Less elegant than using Modulith's own registry API,
 * more certain to actually compile and run.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublicationHealthScheduler {

    private final JdbcTemplate jdbc;
    private final EmailService emailService;

    @Value("${handyflow.ops-alert-email:#{null}}")
    private String opsAlertEmail;

    private static final Duration STUCK_THRESHOLD = Duration.ofHours(2);

    @Scheduled(fixedDelay = 30 * 60 * 1000) // every 30 minutes
    public void checkForStuckEventPublications() {
        Instant cutoff = Instant.now().minus(STUCK_THRESHOLD);

        List<StuckPublication> stuck;
        try {
            stuck = jdbc.query(
                    """
                    SELECT event_type, listener_id, publication_date
                    FROM event_publication
                    WHERE completion_date IS NULL
                    AND publication_date < ?
                    ORDER BY publication_date ASC
                    """,
                    (rs, rowNum) -> new StuckPublication(
                            rs.getString("event_type"),
                            rs.getString("listener_id"),
                            rs.getTimestamp("publication_date").toInstant()),
                    Timestamp.from(cutoff));
        } catch (Exception e) {
            // Most likely cause if this fires: event_publication doesn't
            // exist because spring-modulith-starter-jpa was never added —
            // see class Javadoc. Logged at WARN, not ERROR, since a
            // missing table is a setup/config issue to fix once, not an
            // ongoing operational alert to page anyone on repeatedly.
            log.warn("Could not query event_publication — table may not exist yet " +
                    "(see EventPublicationHealthScheduler's class Javadoc for the likely " +
                    "missing-dependency cause): {}", e.getMessage());
            return;
        }

        if (stuck.isEmpty()) return;

        log.error("EventPublicationHealthScheduler: {} event publication(s) have been " +
                        "incomplete for over {} — a listener is likely broken, not just slow. " +
                        "Oldest: eventType={} listenerId={} since={}",
                stuck.size(), STUCK_THRESHOLD,
                stuck.get(0).eventType(), stuck.get(0).listenerId(), stuck.get(0).publicationDate());

        if (opsAlertEmail != null && !opsAlertEmail.isBlank()) {
            try {
                String rows = stuck.stream()
                        .limit(20) // cap the email body, not the count reported above
                        .map(p -> "<tr><td>" + p.eventType() + "</td><td>" + p.listenerId()
                                + "</td><td>" + p.publicationDate() + "</td></tr>")
                        .reduce("", String::concat);

                emailService.send(opsAlertEmail,
                        "HandyFlow: " + stuck.size() + " stuck event publication(s)",
                        "<p>" + stuck.size() + " Spring Modulith event publication(s) have been " +
                                "incomplete for over " + STUCK_THRESHOLD.toHours() + " hours. " +
                                "This usually means an event listener is throwing on every attempt, " +
                                "not that it's merely slow — check application logs for the " +
                                "underlying exception.</p>" +
                                "<table border='1' cellpadding='4'><tr><th>Event Type</th>" +
                                "<th>Listener</th><th>Since</th></tr>" + rows + "</table>");
            } catch (Exception e) {
                log.error("Failed to send stuck-event-publication alert email", e);
            }
        } else {
            log.warn("No handyflow.ops-alert-email configured — stuck event publications " +
                    "logged above but no email alert sent. Set this property to enable alerting.");
        }
    }

    private record StuckPublication(String eventType, String listenerId, Instant publicationDate) {}
}