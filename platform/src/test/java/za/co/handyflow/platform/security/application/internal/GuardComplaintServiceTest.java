package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.identity.TenantNumberingFacade;
import za.co.handyflow.platform.security.domain.model.ComplaintWorkflow.Status;
import za.co.handyflow.platform.security.domain.model.GuardComplaint;
import za.co.handyflow.platform.security.domain.model.GuardComplaintEvent;
import za.co.handyflow.platform.security.domain.repository.GuardComplaintEventRepository;
import za.co.handyflow.platform.security.domain.repository.GuardComplaintRepository;
import za.co.handyflow.platform.security.domain.repository.GuardRepository;
import za.co.handyflow.platform.security.domain.repository.SiteRepository;
import za.co.handyflow.platform.security.dto.GuardComplaintDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Complaint service: validation, the step order, required notes, evidence rules and the timeline. */
@ExtendWith(MockitoExtension.class)
class GuardComplaintServiceTest {

    @Mock private GuardComplaintRepository repository;
    @Mock private GuardComplaintEventRepository eventRepository;
    @Mock private GuardRepository guardRepository;
    @Mock private SiteRepository siteRepository;
    @Mock private GuardService guardService;
    @Mock private EvidenceFacade evidenceFacade;
    @Mock private TenantNumberingFacade numberingFacade;

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
    private final UUID guardId = UUID.randomUUID();

    private GuardComplaintService service() {
        return new GuardComplaintService(repository, eventRepository, guardRepository, siteRepository, guardService, evidenceFacade, numberingFacade);
    }

    @BeforeEach
    void numbering() { lenient().when(numberingFacade.next(TENANT, "GUARD_COMPLAINT", "CMP")).thenReturn("CMP-0001"); }

    private SaveComplaintRequest req(String category, String severity, LocalDate on, String description) {
        return new SaveComplaintRequest(guardId, null, on, category, severity, description, "CLIENT", "Mrs Dlamini", "082 000 0000", null);
    }

    /** A complaint in the given state, registered with the mocked repository. */
    private GuardComplaint complaint(Status target) {
        var c = GuardComplaint.log(TENANT, "CMP-0001", guardId, null, TODAY.minusDays(2),
                za.co.handyflow.platform.security.domain.model.ComplaintWorkflow.Category.LATENESS,
                za.co.handyflow.platform.security.domain.model.ComplaintWorkflow.Severity.MEDIUM, "Late three times", 
                za.co.handyflow.platform.security.domain.model.ComplaintWorkflow.ComplainantType.CLIENT, null, null, null, USER, "Sam");
        lenient().when(repository.findForTenant(TENANT, c.getId())).thenReturn(Optional.of(c));
        if (target.ordinal() >= Status.UNDER_INVESTIGATION.ordinal() && target != Status.WITHDRAWN) c.startInvestigation("Thandi");
        return c;
    }

    @Test @DisplayName("Logs a complaint with a generated number and a LOGGED event")
    void logs() {
        var out = service().log(TENANT, req("lateness", "high", TODAY.minusDays(1), "Arrived 40 minutes late"), USER, "Sam", TODAY);
        assertThat(out.summary().complaintNumber()).isEqualTo("CMP-0001");
        assertThat(out.summary().status()).isEqualTo("RECEIVED");
        assertThat(out.summary().category()).isEqualTo("LATENESS");
        assertThat(out.allowedSteps()).containsExactlyInAnyOrder("START", "WITHDRAW");
        verify(repository).save(any(GuardComplaint.class));
        verify(eventRepository).save(any(GuardComplaintEvent.class));
    }

    @Test @DisplayName("Rejects an unknown category, a future date and a blank description")
    void validation() {
        assertThatThrownBy(() -> service().log(TENANT, req("TARDY", "LOW", TODAY, "x"), USER, "Sam", TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("Unknown category");
        assertThatThrownBy(() -> service().log(TENANT, req("LATENESS", "LOW", TODAY.plusDays(1), "x"), USER, "Sam", TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("future");
        assertThatThrownBy(() -> service().log(TENANT, req("LATENESS", "LOW", TODAY, " "), USER, "Sam", TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("Describe");
        verify(repository, never()).save(any());
    }

    @Test @DisplayName("An unknown site is rejected")
    void unknownSite() {
        var siteId = UUID.randomUUID();
        when(siteRepository.findActiveById(TENANT, siteId)).thenReturn(Optional.empty());
        var r = new SaveComplaintRequest(guardId, siteId, TODAY, "LATENESS", "LOW", "x", "CLIENT", null, null, null);
        assertThatThrownBy(() -> service().log(TENANT, r, USER, "Sam", TODAY)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("Steps must follow the workflow order")
    void order() {
        var c = complaint(Status.RECEIVED);
        assertThatThrownBy(() -> service().finding(TENANT, c.getId(), new FindingRequest("SUBSTANTIATED", "x"), USER, "Sam"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not available");
        assertThatThrownBy(() -> service().close(TENANT, c.getId(), new CloseRequest("done"), USER, "Sam"))
                .isInstanceOf(HandyFlowException.class);
        var out = service().start(TENANT, c.getId(), new StartInvestigationRequest("Thandi"), USER, "Sam");
        assertThat(out.summary().status()).isEqualTo("UNDER_INVESTIGATION");
        assertThat(out.investigatorName()).isEqualTo("Thandi");
    }

    @Test @DisplayName("A finding needs a note")
    void findingNote() {
        var c = complaint(Status.UNDER_INVESTIGATION);
        assertThatThrownBy(() -> service().finding(TENANT, c.getId(), new FindingRequest("SUBSTANTIATED", " "), USER, "Sam"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("found");
        var out = service().finding(TENANT, c.getId(), new FindingRequest("substantiated", "Gate log confirms it"), USER, "Sam");
        assertThat(out.summary().finding()).isEqualTo("SUBSTANTIATED");
        assertThat(out.summary().status()).isEqualTo("FINDING_MADE");
    }

    @Test @DisplayName("Unsubstantiated complaints allow only no action, and may close straight away")
    void unsubstantiated() {
        var c = complaint(Status.UNDER_INVESTIGATION);
        service().finding(TENANT, c.getId(), new FindingRequest("UNSUBSTANTIATED", "No evidence"), USER, "Sam");
        assertThatThrownBy(() -> service().action(TENANT, c.getId(), new ActionRequest("WRITTEN_WARNING", "x"), USER, "Sam"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not substantiated");
        var out = service().close(TENANT, c.getId(), new CloseRequest("Complainant informed"), USER, "Sam");
        assertThat(out.summary().status()).isEqualTo("CLOSED");
        assertThat(out.allowedSteps()).isEmpty();
    }

    @Test @DisplayName("Substantiated: no action needs a note, a warning then closure works, and closure needs a resolution")
    void substantiatedPath() {
        var c = complaint(Status.UNDER_INVESTIGATION);
        service().finding(TENANT, c.getId(), new FindingRequest("SUBSTANTIATED", "Confirmed"), USER, "Sam");
        assertThatThrownBy(() -> service().action(TENANT, c.getId(), new ActionRequest("NO_ACTION", null), USER, "Sam"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("why no action");
        assertThatThrownBy(() -> service().close(TENANT, c.getId(), new CloseRequest("done"), USER, "Sam"))
                .isInstanceOf(HandyFlowException.class);
        service().action(TENANT, c.getId(), new ActionRequest("VERBAL_WARNING", null), USER, "Sam");
        assertThatThrownBy(() -> service().close(TENANT, c.getId(), new CloseRequest(" "), USER, "Sam"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("resolved");
        var out = service().close(TENANT, c.getId(), new CloseRequest("Guard warned and signed"), USER, "Sam");
        assertThat(out.summary().status()).isEqualTo("CLOSED");
        assertThat(out.summary().action()).isEqualTo("VERBAL_WARNING");
    }

    @Test @DisplayName("Withdrawal needs a reason and is final")
    void withdraw() {
        var c = complaint(Status.RECEIVED);
        assertThatThrownBy(() -> service().withdraw(TENANT, c.getId(), new WithdrawRequest(" "), USER, "Sam"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("reason");
        var out = service().withdraw(TENANT, c.getId(), new WithdrawRequest("Complainant retracted"), USER, "Sam");
        assertThat(out.summary().status()).isEqualTo("WITHDRAWN");
        assertThat(out.withdrawnReason()).isEqualTo("Complainant retracted");
        assertThatThrownBy(() -> service().start(TENANT, c.getId(), null, USER, "Sam")).isInstanceOf(HandyFlowException.class);
    }

    @Test @DisplayName("Details cannot be edited after a finding, and a complaint cannot move to another guard")
    void edit() {
        var c = complaint(Status.UNDER_INVESTIGATION);
        var other = new SaveComplaintRequest(UUID.randomUUID(), null, TODAY, "LATENESS", "LOW", "x", "CLIENT", null, null, null);
        assertThatThrownBy(() -> service().update(TENANT, c.getId(), other, USER, "Sam")).isInstanceOf(HandyFlowException.class).hasMessageContaining("different guard");
        var ok = service().update(TENANT, c.getId(), req("NEGLIGENCE", "HIGH", TODAY.minusDays(1), "Left post"), USER, "Sam");
        assertThat(ok.summary().category()).isEqualTo("NEGLIGENCE");
        service().finding(TENANT, c.getId(), new FindingRequest("INCONCLUSIVE", "Unclear"), USER, "Sam");
        assertThatThrownBy(() -> service().update(TENANT, c.getId(), req("LATENESS", "LOW", TODAY, "x"), USER, "Sam"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("finding");
    }

    @Test @DisplayName("A complaint from another tenant is not found")
    void tenantScoping() {
        var id = UUID.randomUUID();
        when(repository.findForTenant(TENANT, id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().get(TENANT, id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("Evidence is refused on a closed complaint and downloads only the complaint's own files")
    void evidence() {
        var c = complaint(Status.UNDER_INVESTIGATION);
        var mine = UUID.randomUUID();
        when(evidenceFacade.listFor(TENANT, "security", "GuardComplaint", c.getId()))
                .thenReturn(List.of(new EvidenceResponse(mine, "photo.jpg", "image/jpeg", 5L, "Complaint evidence", "ACTIVE", "Sam", Instant.now())));
        service().download(TENANT, c.getId(), mine);
        verify(evidenceFacade).download(TENANT, mine);
        assertThatThrownBy(() -> service().download(TENANT, c.getId(), UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);

        service().finding(TENANT, c.getId(), new FindingRequest("UNSUBSTANTIATED", "n"), USER, "Sam");
        service().close(TENANT, c.getId(), new CloseRequest("done"), USER, "Sam");
        var file = new org.springframework.mock.web.MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1});
        assertThatThrownBy(() -> service().attach(TENANT, c.getId(), file, null, USER, "Sam"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("closed");
        verify(evidenceFacade, never()).attach(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("Counts open complaints and the last 90 days, ignoring withdrawn ones")
    void counts() {
        ComplaintSummary open = row(TODAY.minusDays(10), "UNDER_INVESTIGATION", null, true);
        ComplaintSummary sub = row(TODAY.minusDays(30), "CLOSED", "SUBSTANTIATED", false);
        ComplaintSummary old = row(TODAY.minusDays(200), "CLOSED", "SUBSTANTIATED", false);
        ComplaintSummary withdrawn = row(TODAY.minusDays(5), "WITHDRAWN", null, false);
        var counts = GuardComplaintService.counts(List.of(open, sub, old, withdrawn), TODAY);
        assertThat(counts.open()).isEqualTo(1);
        assertThat(counts.last90Days()).isEqualTo(2);
        assertThat(counts.substantiatedLast90Days()).isEqualTo(1);
    }

    private static ComplaintSummary row(LocalDate on, String status, String finding, boolean open) {
        return new ComplaintSummary(UUID.randomUUID(), "CMP-1", UUID.randomUUID(), "G", null, null, on, "LATENESS", "LOW", status, finding, null, open, false, Instant.now());
    }
}
