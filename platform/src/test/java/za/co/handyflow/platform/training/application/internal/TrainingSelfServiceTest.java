package za.co.handyflow.platform.training.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.domain.model.TrainingCertificate;
import za.co.handyflow.platform.training.domain.repository.TrainingCertificateRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingCourseRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingEnrollmentRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingSessionRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** An employee sees only their own certificates; anyone else's looks like one that does not exist. */
class TrainingSelfServiceTest {

    private final TenantId tenant = TenantId.of(UUID.randomUUID());
    private final UUID userId = UUID.randomUUID();
    private final UUID meId = UUID.randomUUID();
    private final HrFacade hrFacade = mock(HrFacade.class);
    private final TrainingCertificateRepository certificates = mock(TrainingCertificateRepository.class);
    private final TrainingSelfService service = new TrainingSelfService(hrFacade, mock(TrainingEnrollmentRepository.class),
            mock(TrainingSessionRepository.class), mock(TrainingCourseRepository.class), certificates);

    private EmployeeSummary me() {
        return new EmployeeSummary(meId, "E001", "Thandi", "Nkosi", "Thandi Nkosi", "t@example.com", null, "Clerk", "Admin", "ACTIVE");
    }

    private TrainingCertificate certificate(UUID employeeId, String status) {
        TrainingCertificate c = TrainingCertificate.create(tenant, UUID.randomUUID(), employeeId, "Someone", "First Aid", "TC-1",
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        if ("REVOKED".equals(status)) c.revoke("Issued in error");
        return c;
    }

    @Test
    void anEmployeeCanOpenTheirOwnCertificate() {
        TrainingCertificate mine = certificate(meId, "VALID");
        when(hrFacade.findEmployeeByUserId(tenant, userId)).thenReturn(Optional.of(me()));
        when(certificates.findByTenantAndId(any(), eq(mine.getId()))).thenReturn(Optional.of(mine));

        assertThat(service.ownCertificate(tenant, userId, mine.getId())).isSameAs(mine);
    }

    @Test
    void someoneElsesCertificateIsNotFound() {
        TrainingCertificate theirs = certificate(UUID.randomUUID(), "VALID");
        when(hrFacade.findEmployeeByUserId(tenant, userId)).thenReturn(Optional.of(me()));
        when(certificates.findByTenantAndId(any(), eq(theirs.getId()))).thenReturn(Optional.of(theirs));

        assertThatThrownBy(() -> service.ownCertificate(tenant, userId, theirs.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aWithdrawnCertificateHasNoPdf() {
        TrainingCertificate withdrawn = certificate(meId, "REVOKED");
        when(hrFacade.findEmployeeByUserId(tenant, userId)).thenReturn(Optional.of(me()));
        when(certificates.findByTenantAndId(any(), eq(withdrawn.getId()))).thenReturn(Optional.of(withdrawn));

        assertThatThrownBy(() -> service.ownCertificate(tenant, userId, withdrawn.getId())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aLoginWithNoEmployeeRecordGetsNothing() {
        when(hrFacade.findEmployeeByUserId(tenant, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.mine(tenant, userId)).isInstanceOf(ResourceNotFoundException.class);
    }
}
