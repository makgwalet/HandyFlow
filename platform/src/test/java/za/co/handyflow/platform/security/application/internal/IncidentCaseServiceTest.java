package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.security.domain.model.Incident;
import za.co.handyflow.platform.security.domain.model.IncidentEvent;
import za.co.handyflow.platform.security.domain.repository.IncidentEventRepository;
import za.co.handyflow.platform.security.domain.repository.IncidentRepository;
import za.co.handyflow.platform.security.dto.IncidentCaseDtos.*;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Working an incident: assignment, escalation, notes, reopening and evidence, each with its timeline entry. */
@ExtendWith(MockitoExtension.class)
class IncidentCaseServiceTest {

    @Mock private IncidentRepository incidentRepo;
    @Mock private IncidentEventRepository eventRepo;
    @Mock private IncidentService incidentService;
    @Mock private EvidenceFacade evidenceFacade;

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();

    private IncidentCaseService service() { return new IncidentCaseService(incidentRepo, eventRepo, incidentService, evidenceFacade); }

    private Incident incident(String severity, String status) {
        Incident i = Incident.create(TENANT, UUID.randomUUID(), null, null, "Gate forced", "desc", severity, null, null);
        if ("ACKNOWLEDGED".equals(status)) i.acknowledge();
        if ("RESOLVED".equals(status)) i.resolve();
        lenient().when(incidentRepo.findByIdAndTenantId(i.getId(), TENANT)).thenReturn(Optional.of(i));
        return i;
    }

    private void wroteEvent(String type) { verify(eventRepo).save(argThat((IncidentEvent e) -> e.getEventType().equals(type))); }

    @Test @DisplayName("Assigns an incident and records it on the timeline")
    void assigns() {
        var i = incident("LOW", "OPEN");
        service().assign(TENANT, i.getId(), new AssignRequest("  Thandi Zulu "), USER, "Sam");
        assertThat(i.getAssigneeName()).isEqualTo("Thandi Zulu");
        assertThat(i.getAssignedAt()).isNotNull();
        wroteEvent("ASSIGNED");
    }

    @Test @DisplayName("A blank assignee is refused, and a resolved incident cannot be assigned")
    void assignRules() {
        var i = incident("LOW", "OPEN");
        assertThatThrownBy(() -> service().assign(TENANT, i.getId(), new AssignRequest(" "), USER, "Sam"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("who");
        var done = incident("LOW", "RESOLVED");
        assertThatThrownBy(() -> service().assign(TENANT, done.getId(), new AssignRequest("X"), USER, "Sam"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not available");
    }

    @Test @DisplayName("Escalates one level by default and records the reason")
    void escalatesOneLevel() {
        var i = incident("MEDIUM", "OPEN");
        service().escalate(TENANT, i.getId(), new EscalateRequest(null, "Client threatening to leave"), USER, "Sam");
        assertThat(i.getSeverity()).isEqualTo("HIGH");
        verify(eventRepo).save(argThat((IncidentEvent e) -> e.getEventType().equals("ESCALATED") && e.getNote().contains("medium to high") && e.getNote().contains("Client threatening")));
    }

    @Test @DisplayName("Escalates to a named level, but never down, never without a reason, never past critical")
    void escalationRules() {
        var i = incident("LOW", "OPEN");
        service().escalate(TENANT, i.getId(), new EscalateRequest("critical", "Armed"), USER, "Sam");
        assertThat(i.getSeverity()).isEqualTo("CRITICAL");
        assertThatThrownBy(() -> service().escalate(TENANT, i.getId(), new EscalateRequest(null, "again"), USER, "Sam"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("not available");
        var h = incident("HIGH", "OPEN");
        assertThatThrownBy(() -> service().escalate(TENANT, h.getId(), new EscalateRequest("MEDIUM", "x"), USER, "Sam"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("raise");
        assertThatThrownBy(() -> service().escalate(TENANT, h.getId(), new EscalateRequest(null, " "), USER, "Sam"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("reason");
        assertThat(h.getSeverity()).isEqualTo("HIGH");
    }

    @Test @DisplayName("Notes need text and are allowed even after resolution")
    void notes() {
        var done = incident("LOW", "RESOLVED");
        service().note(TENANT, done.getId(), new NoteRequest("Client called back, satisfied"), USER, "Sam");
        wroteEvent("NOTE");
        assertThatThrownBy(() -> service().note(TENANT, done.getId(), new NoteRequest(" "), USER, "Sam"))
                .isInstanceOf(HandyFlowException.class);
    }

    @Test @DisplayName("Reopening needs a reason, moves a resolved incident back to acknowledged and clears the resolution time")
    void reopens() {
        var i = incident("LOW", "RESOLVED");
        assertThatThrownBy(() -> service().reopen(TENANT, i.getId(), new ReopenRequest(" "), USER, "Sam")).isInstanceOf(HandyFlowException.class);
        service().reopen(TENANT, i.getId(), new ReopenRequest("Second break-in attempt"), USER, "Sam");
        assertThat(i.getStatus()).isEqualTo("ACKNOWLEDGED");
        assertThat(i.getResolvedAt()).isNull();
        wroteEvent("REOPENED");
        var open = incident("LOW", "OPEN");
        assertThatThrownBy(() -> service().reopen(TENANT, open.getId(), new ReopenRequest("x"), USER, "Sam")).isInstanceOf(HandyFlowException.class);
    }

    @Test @DisplayName("Another tenant's incident is not found")
    void tenantScoping() {
        var id = UUID.randomUUID();
        when(incidentRepo.findByIdAndTenantId(id, TENANT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().get(TENANT, id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("Evidence is refused on a resolved incident and downloads only the incident's own files")
    void evidence() {
        var i = incident("LOW", "OPEN");
        var mine = UUID.randomUUID();
        when(evidenceFacade.listFor(TENANT, "security", "Incident", i.getId()))
                .thenReturn(List.of(new EvidenceResponse(mine, "gate.jpg", "image/jpeg", 5L, "Photo", "ACTIVE", "Sam", Instant.now())));
        service().download(TENANT, i.getId(), mine);
        verify(evidenceFacade).download(TENANT, mine);
        assertThatThrownBy(() -> service().download(TENANT, i.getId(), UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);

        var done = incident("LOW", "RESOLVED");
        var file = new org.springframework.mock.web.MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{1});
        assertThatThrownBy(() -> service().attach(TENANT, done.getId(), file, null, USER, "Sam")).isInstanceOf(HandyFlowException.class);
        verify(evidenceFacade, never()).attach(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("The case view lists the actions available now")
    void caseView() {
        var i = incident("HIGH", "ACKNOWLEDGED");
        var out = service().get(TENANT, i.getId());
        assertThat(out.allowedActions()).contains("RESOLVE", "ESCALATE", "ASSIGN").doesNotContain("ACKNOWLEDGE", "REOPEN");
    }

    @Test @DisplayName("A guard can attach a file to an incident they reported")
    void guardAttachesToOwnIncident() {
        UUID guard = UUID.randomUUID();
        Incident i = Incident.create(TENANT, UUID.randomUUID(), null, guard, "Gate forced", "desc", "LOW", null, null);
        lenient().when(incidentRepo.findByIdAndTenantId(i.getId(), TENANT)).thenReturn(Optional.of(i));
        var file = new org.springframework.mock.web.MockMultipartFile("file", "gate.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(evidenceFacade.listFor(any(), any(), any(), any())).thenReturn(List.of());
        EvidenceResponse saved = org.mockito.Mockito.mock(EvidenceResponse.class);
        when(saved.fileName()).thenReturn("gate.jpg");
        when(evidenceFacade.attach(any(), any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(saved);

        service().attachByGuard(TENANT, i.getId(), guard, file, null, "Lerato");

        wroteEvent("EVIDENCE_ADDED");
    }

    @Test @DisplayName("A guard cannot attach to another guard's incident, and the other guard's incident reads as not found")
    void guardCannotAttachToSomeoneElsesIncident() {
        Incident i = Incident.create(TENANT, UUID.randomUUID(), null, UUID.randomUUID(), "Gate forced", "desc", "LOW", null, null);
        lenient().when(incidentRepo.findByIdAndTenantId(i.getId(), TENANT)).thenReturn(Optional.of(i));
        var file = new org.springframework.mock.web.MockMultipartFile("file", "gate.jpg", "image/jpeg", new byte[]{1});

        assertThatThrownBy(() -> service().attachByGuard(TENANT, i.getId(), UUID.randomUUID(), file, null, "Mallory"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(evidenceFacade, never()).attach(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test @DisplayName("An incident with no guard on it cannot take guard uploads")
    void incidentWithNoGuardRefusesGuardUploads() {
        Incident i = incident("LOW", "OPEN"); // created with no guard
        var file = new org.springframework.mock.web.MockMultipartFile("file", "gate.jpg", "image/jpeg", new byte[]{1});
        assertThatThrownBy(() -> service().attachByGuard(TENANT, i.getId(), UUID.randomUUID(), file, null, "X"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("A guard is capped at 20 files per incident")
    void guardUploadsAreCapped() {
        UUID guard = UUID.randomUUID();
        Incident i = Incident.create(TENANT, UUID.randomUUID(), null, guard, "Gate forced", "desc", "LOW", null, null);
        lenient().when(incidentRepo.findByIdAndTenantId(i.getId(), TENANT)).thenReturn(Optional.of(i));
        List<EvidenceResponse> twenty = java.util.Collections.nCopies(20, org.mockito.Mockito.mock(EvidenceResponse.class));
        when(evidenceFacade.listFor(any(), any(), any(), any())).thenReturn(twenty);
        var file = new org.springframework.mock.web.MockMultipartFile("file", "gate.jpg", "image/jpeg", new byte[]{1});

        assertThatThrownBy(() -> service().attachByGuard(TENANT, i.getId(), guard, file, null, "Lerato"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("20 files");
    }
}
