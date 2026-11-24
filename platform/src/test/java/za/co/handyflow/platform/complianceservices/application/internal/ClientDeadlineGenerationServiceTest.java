package za.co.handyflow.platform.complianceservices.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.complianceservices.domain.model.*;
import za.co.handyflow.platform.complianceservices.domain.repository.*;
import za.co.handyflow.platform.complianceservices.dto.DeadlineGenerationDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientDeadlineGenerationServiceTest {

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 24);

    @Mock ComplianceClientRepository clients;
    @Mock ComplianceClientObligationRepository assignments;
    @Mock ComplianceObligationRepository obligations;
    @Mock ComplianceObligationVersionRepository versions;
    @Mock ClientComplianceDeadlineRepository deadlines;

    private ClientDeadlineGenerationService service;
    private ComplianceClient client;
    private ComplianceObligation obligation;
    private ComplianceObligationVersion version;

    @BeforeEach
    void setUp() {
        service = new ClientDeadlineGenerationService(clients, assignments, obligations, versions, deadlines);
        client = ComplianceClient.create(TENANT, "Acme", null, null, null, null, USER);
        when(clients.findByIdForTenant(TENANT, client.getId())).thenReturn(Optional.of(client));
        obligation = ComplianceObligation.create(TENANT, "CIPC-AR", USER);
        version = ComplianceObligationVersion.draft(TENANT, obligation.getId(), 1, USER);
    }

    private void rule(String type, Integer month, Integer day, Integer offDays, Integer offMonths) {
        version.edit(new ComplianceObligationVersion.Content("Annual return", null, "Act s33", null, null, "ANNUAL", type, month, day, offDays, offMonths,
                null, "HIGH", "CLIENT", null, null), USER);
    }

    private ComplianceClientObligation assigned(String state) {
        return ComplianceClientObligation.suggested(TENANT, client.getId(), obligation.getId(), version.getId(), 1, new ApplicabilityRules.Evaluation(state, List.of(), false));
    }

    private void wire(ComplianceClientObligation a, List<ClientComplianceDeadline> existing) {
        when(assignments.findForClient(TENANT, client.getId())).thenReturn(List.of(a));
        when(obligations.findAllForTenant(TENANT)).thenReturn(List.of(obligation));
        when(versions.findAllForTenant(TENANT)).thenReturn(List.of(version));
        when(deadlines.findGeneratedForClient(TENANT, client.getId())).thenReturn(existing);
    }

    @Test @DisplayName("creates the next date for an obligation that applies, tied to the obligation and version")
    void creates() {
        rule("ANNUAL_FIXED", 1, 31, null, null);
        wire(assigned("APPLICABLE"), List.of());
        var r = service.generate(TENANT, client.getId(), null, USER, TODAY);
        assertThat(r.created()).isEqualTo(1);
        ArgumentCaptor<ClientComplianceDeadline> cap = ArgumentCaptor.forClass(ClientComplianceDeadline.class);
        verify(deadlines).save(cap.capture());
        assertThat(cap.getValue().getDueDate()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(cap.getValue().isGenerated()).isTrue();
        assertThat(cap.getValue().getObligationId()).isEqualTo(obligation.getId());
        assertThat(cap.getValue().getObligationVersionNumber()).isEqualTo(1);
    }

    @Test @DisplayName("running again adds nothing when the date is already there")
    void idempotent() {
        rule("ANNUAL_FIXED", 1, 31, null, null);
        var existing = ClientComplianceDeadline.generated(TENANT, client.getId(), obligation.getId(), 1, "CLIENT", "x", LocalDate.of(2027, 1, 31), USER);
        wire(assigned("APPLICABLE"), List.of(existing));
        var r = service.generate(TENANT, client.getId(), null, USER, TODAY);
        assertThat(r.created()).isEqualTo(0);
        assertThat(r.unchanged()).isEqualTo(1);
        verify(deadlines, never()).save(any());
    }

    @Test @DisplayName("a pending date the rule no longer produces is cancelled with a reason, not deleted")
    void cancelsOldDates() {
        rule("ANNUAL_FIXED", 3, 31, null, null);
        var stale = ClientComplianceDeadline.generated(TENANT, client.getId(), obligation.getId(), 1, "CLIENT", "x", LocalDate.of(2027, 1, 31), USER);
        wire(assigned("APPLICABLE"), List.of(stale));
        var r = service.generate(TENANT, client.getId(), null, USER, TODAY);
        assertThat(r.cancelled()).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo("CANCELLED");
        assertThat(stale.getCancelledReason()).contains("rule changed");
        verify(deadlines, never()).delete(any());
    }

    @Test @DisplayName("a date already done or past is never cancelled")
    void keepsDoneAndOverdue() {
        rule("ANNUAL_FIXED", 3, 31, null, null);
        var done = ClientComplianceDeadline.generated(TENANT, client.getId(), obligation.getId(), 1, "CLIENT", "x", LocalDate.of(2027, 1, 31), USER);
        done.markDone(USER);
        var overdue = ClientComplianceDeadline.generated(TENANT, client.getId(), obligation.getId(), 1, "CLIENT", "x", LocalDate.of(2026, 10, 31), USER);
        wire(assigned("APPLICABLE"), List.of(done, overdue));
        service.generate(TENANT, client.getId(), null, USER, TODAY);
        assertThat(done.getStatus()).isEqualTo("DONE");
        assertThat(overdue.getStatus()).isEqualTo("PENDING");
    }

    @Test @DisplayName("an obligation that stopped applying has its pending dates cancelled")
    void notApplicableCancels() {
        rule("ANNUAL_FIXED", 1, 31, null, null);
        var pending = ClientComplianceDeadline.generated(TENANT, client.getId(), obligation.getId(), 1, "CLIENT", "x", LocalDate.of(2027, 1, 31), USER);
        wire(assigned("NOT_APPLICABLE"), List.of(pending));
        var r = service.generate(TENANT, client.getId(), null, USER, TODAY);
        assertThat(r.cancelled()).isEqualTo(1);
        assertThat(pending.getCancelledReason()).contains("no longer applies");
    }

    @Test @DisplayName("an undecided obligation is left alone")
    void undecidedUntouched() {
        rule("ANNUAL_FIXED", 1, 31, null, null);
        wire(assigned("PENDING"), List.of());
        var r = service.generate(TENANT, client.getId(), null, USER, TODAY);
        assertThat(r.created()).isEqualTo(0);
        verify(deadlines, never()).save(any());
    }

    @Test @DisplayName("a rule that needs a key date nobody entered is reported and nothing is cancelled")
    void missingKeyDate() {
        rule("ANNUAL_AFTER_FYE", null, null, null, 5);
        var existing = ClientComplianceDeadline.generated(TENANT, client.getId(), obligation.getId(), 1, "CLIENT", "x", LocalDate.of(2027, 7, 31), USER);
        wire(assigned("APPLICABLE"), List.of(existing));
        var r = service.generate(TENANT, client.getId(), null, USER, TODAY);
        assertThat(r.skipped()).hasSize(1);
        assertThat(r.skipped().get(0).reason()).contains("financial year end");
        assertThat(existing.getStatus()).isEqualTo("PENDING");
    }

    @Test @DisplayName("with no obligations assigned it points at the suggestions")
    void noObligations() {
        when(assignments.findForClient(TENANT, client.getId())).thenReturn(List.of());
        assertThatThrownBy(() -> service.generate(TENANT, client.getId(), null, USER, TODAY)).isInstanceOf(HandyFlowException.class).hasMessageContaining("Update the suggestions");
    }

    @Test @DisplayName("a look-ahead beyond two years is refused")
    void windowTooLong() {
        assertThatThrownBy(() -> service.generate(TENANT, client.getId(), 30, USER, TODAY)).isInstanceOf(HandyFlowException.class);
    }

    @Test @DisplayName("bad key dates are a 400 and nothing is saved")
    void badKeyDates() {
        assertThatThrownBy(() -> service.setKeyDates(TENANT, client.getId(), new KeyDatesRequest(13, null), USER, TODAY)).isInstanceOf(HandyFlowException.class);
        verify(clients, never()).save(any());
        var v = service.setKeyDates(TENANT, client.getId(), new KeyDatesRequest(2, LocalDate.of(2019, 3, 15)), USER, TODAY);
        assertThat(v.financialYearEndMonth()).isEqualTo(2);
        verify(clients, times(1)).save(client);
    }
}
