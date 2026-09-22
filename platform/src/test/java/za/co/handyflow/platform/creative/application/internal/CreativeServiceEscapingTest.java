package za.co.handyflow.platform.creative.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.handyflow.platform.approvals.application.ApprovalFacade;
import za.co.handyflow.platform.approvals.domain.model.ApprovalRule;
import za.co.handyflow.platform.creative.domain.model.CreProof;
import za.co.handyflow.platform.creative.domain.repository.CreDeliverableRepository;
import za.co.handyflow.platform.creative.domain.repository.CreJobRepository;
import za.co.handyflow.platform.creative.domain.repository.CreProofApproverRepository;
import za.co.handyflow.platform.creative.domain.repository.CreProofCommentRepository;
import za.co.handyflow.platform.creative.domain.repository.CreProofRepository;
import za.co.handyflow.platform.shared.EmailService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for CreativeService, found while checking it as a
 * candidate for the EmailTemplates shared-template migration: NOT
 * migrated (its per-message <h1> heading text and "Creative Studio —
 * Proof Review" eyebrow, and creative's own allowedDependencies not
 * including identity, mean it can't cleanly become wrap()/wrapForTenant()
 * without losing information or a boundary change). Most of this file
 * already escapes carefully via HtmlUtils.htmlEscape — but 4 of its more
 * complex templates (buildRejectionNotificationEmail,
 * buildUnapprovedReminderEmail, buildApprovalEmail, buildApproverEmail)
 * had jobTitle, tenantName, approverName, and a free-text customMessage
 * field all interpolated with no escaping — same bug category as the 41
 * already found and fixed elsewhere this session.
 */
@ExtendWith(MockitoExtension.class)
class CreativeServiceEscapingTest {

    @Mock private CreJobRepository jobRepo;
    @Mock private CreProofRepository proofRepo;
    @Mock private CreProofApproverRepository approverRepo;
    @Mock private CreProofCommentRepository commentRepo;
    @Mock private CreDeliverableRepository deliverableRepo;
    @Mock private EmailService emailService;
    @Mock private JdbcTemplate jdbc;
    @Mock private ApprovalFacade approvalFacade;

    private static final String PAYLOAD = "<script>alert(1)</script>";
    private static final String ESCAPED = "&lt;script&gt;alert(1)&lt;/script&gt;";

    private CreativeService service() {
        return new CreativeService(jobRepo, proofRepo, approverRepo, commentRepo,
                deliverableRepo, emailService, jdbc, approvalFacade);
    }

    private CreProof sampleProof() {
        return CreProof.create(UUID.randomUUID(), UUID.randomUUID(), 1,
                "Sample Proof", "https://example.com/file.pdf", "file.pdf",
                "application/pdf", null, null, UUID.randomUUID());
    }

    @Test
    @DisplayName("buildRejectionNotificationEmail escapes jobTitle")
    void buildRejectionNotificationEmail_escapesJobTitle() {
        String html = service().buildRejectionNotificationEmail(PAYLOAD, 1, "Needs more contrast");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("buildUnapprovedReminderEmail escapes tenantName and jobTitle")
    void buildUnapprovedReminderEmail_escapesNames() {
        String html = service().buildUnapprovedReminderEmail(PAYLOAD, PAYLOAD, 1, "https://example.com/approve");
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("buildApprovalEmail escapes tenantName and customMessage")
    void buildApprovalEmail_escapesTenantNameAndCustomMessage() {
        String html = service().buildApprovalEmail(PAYLOAD, sampleProof(), "https://example.com/approve", PAYLOAD);
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }

    @Test
    @DisplayName("buildApproverEmail escapes tenantName, approverName, and customMessage")
    void buildApproverEmail_escapesNames() {
        String html = service().buildApproverEmail(PAYLOAD, sampleProof(), PAYLOAD, 1,
                ApprovalRule.ApprovalMode.SEQUENTIAL, "https://example.com/approve", PAYLOAD);
        assertThat(html).doesNotContain(PAYLOAD);
        assertThat(html).contains(ESCAPED);
    }
}
