package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.evidence.dto.EvidenceResponse;
import za.co.handyflow.platform.security.domain.model.GuardCompetency;
import za.co.handyflow.platform.security.domain.repository.GuardCompetencyRepository;
import za.co.handyflow.platform.security.dto.SaveGuardCompetencyRequest;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Competency rules: validation, verification needs a certificate, edits remove verification, file ownership. */
@ExtendWith(MockitoExtension.class)
class GuardCompetencyServiceTest {

    @Mock private GuardCompetencyRepository repository;
    @Mock private GuardService guardService;
    @Mock private EvidenceFacade evidenceFacade;

    private static final TenantId TENANT = TenantId.generate();
    private static final UUID USER = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
    private final UUID guardId = UUID.randomUUID();

    private GuardCompetencyService service() { return new GuardCompetencyService(repository, guardService, evidenceFacade); }

    private static SaveGuardCompetencyRequest req(String type, String title, LocalDate issue, LocalDate expiry, boolean required) {
        return new SaveGuardCompetencyRequest(type, title, "St John", issue, expiry, "REF-1", required, null);
    }

    private GuardCompetency existing() {
        var c = GuardCompetency.create(TENANT, guardId, GuardCompetency.Type.FIRST_AID, null, "St John",
                TODAY.minusMonths(6), TODAY.plusYears(1), null, true, null, USER);
        lenient().when(repository.findActiveForGuardById(TENANT, guardId, c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    private static EvidenceResponse ev(UUID id) { return new EvidenceResponse(id, "cert.pdf", "application/pdf", 5L, "Certificate", "ACTIVE", "Sam", Instant.now()); }

    @Test @DisplayName("Creates a competency and reports it as awaiting a certificate")
    void creates() {
        var out = service().create(TENANT, guardId, req("first_aid", null, TODAY.minusDays(10), TODAY.plusYears(2), true), USER, TODAY);
        assertThat(out.competencyType()).isEqualTo("FIRST_AID");
        assertThat(out.required()).isTrue();
        assertThat(out.state()).isEqualTo("INCOMPLETE");
        assertThat(out.detail()).isEqualTo("no certificate attached");
        verify(repository).save(any(GuardCompetency.class));
    }

    @Test @DisplayName("Rejects unknown types, a missing name for Other, future issue dates and expiry before issue")
    void validation() {
        assertThatThrownBy(() -> service().create(TENANT, guardId, req("JUGGLING", null, null, null, false), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("Unknown competency type");
        assertThatThrownBy(() -> service().create(TENANT, guardId, req("OTHER", " ", null, null, false), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("name");
        assertThatThrownBy(() -> service().create(TENANT, guardId, req("DRIVER", null, TODAY.plusDays(1), null, false), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("future");
        assertThatThrownBy(() -> service().create(TENANT, guardId, req("DRIVER", null, TODAY, TODAY.minusDays(1), false), USER, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("before the issue date");
        verify(repository, never()).save(any());
    }

    @Test @DisplayName("Cannot verify without a certificate")
    void verifyNeedsEvidence() {
        var c = existing();
        when(evidenceFacade.listFor(TENANT, "security", "GuardCompetency", c.getId())).thenReturn(List.of());
        assertThatThrownBy(() -> service().verify(TENANT, guardId, c.getId(), null, USER, "Sam"))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("certificate");
        assertThat(c.isVerified()).isFalse();
    }

    @Test @DisplayName("Verifying records who and when, and the competency then counts as met")
    void verifies() {
        var c = existing();
        when(evidenceFacade.listFor(TENANT, "security", "GuardCompetency", c.getId())).thenReturn(List.of(ev(UUID.randomUUID())));
        var out = service().verify(TENANT, guardId, c.getId(), " Original sighted ", USER, "Security Manager");
        assertThat(out.verifiedByName()).isEqualTo("Security Manager");
        assertThat(out.verificationNote()).isEqualTo("Original sighted");
        assertThat(out.state()).isEqualTo("MET");
    }

    @Test @DisplayName("Editing a verified competency removes the verification")
    void editResetsVerification() {
        var c = existing();
        c.verify(USER, "Sam", null);
        service().update(TENANT, guardId, c.getId(), req("FIRST_AID", null, TODAY.minusMonths(6), TODAY.plusYears(3), true));
        assertThat(c.isVerified()).isFalse();
        assertThat(c.getExpiryDate()).isEqualTo(TODAY.plusYears(3));
    }

    @Test @DisplayName("Removing the last certificate removes the verification; removing one of two keeps it")
    void removingFiles() {
        var c = existing();
        c.verify(USER, "Sam", null);
        UUID one = UUID.randomUUID(), two = UUID.randomUUID();
        when(evidenceFacade.listFor(TENANT, "security", "GuardCompetency", c.getId()))
                .thenReturn(List.of(ev(one), ev(two)))   // ownership check for the first removal
                .thenReturn(List.of(ev(two)))             // after the first removal
                .thenReturn(List.of(ev(two)))             // ownership check for the second removal
                .thenReturn(List.of());                   // after the second removal
        service().removeFile(TENANT, guardId, c.getId(), one);
        assertThat(c.isVerified()).isTrue();
        service().removeFile(TENANT, guardId, c.getId(), two);
        assertThat(c.isVerified()).isFalse();
    }

    @Test @DisplayName("A file from another competency cannot be downloaded or removed through this one")
    void foreignFile() {
        var c = existing();
        when(evidenceFacade.listFor(TENANT, "security", "GuardCompetency", c.getId())).thenReturn(List.of(ev(UUID.randomUUID())));
        UUID other = UUID.randomUUID();
        assertThatThrownBy(() -> service().download(TENANT, guardId, c.getId(), other)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().removeFile(TENANT, guardId, c.getId(), other)).isInstanceOf(ResourceNotFoundException.class);
        verify(evidenceFacade, never()).detach(any(), any());
    }

    @Test @DisplayName("A competency of another guard or tenant is not found")
    void scoping() {
        var c = existing();
        assertThatThrownBy(() -> service().update(TENANT, UUID.randomUUID(), c.getId(), req("FIRST_AID", null, null, null, false)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service().remove(TenantId.generate(), guardId, c.getId(), USER)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("Removal is a soft delete")
    void softDelete() {
        var c = existing();
        service().remove(TENANT, guardId, c.getId(), USER);
        assertThat(c.getDeletedAt()).isNotNull();
        verify(repository).save(c);
    }
}
