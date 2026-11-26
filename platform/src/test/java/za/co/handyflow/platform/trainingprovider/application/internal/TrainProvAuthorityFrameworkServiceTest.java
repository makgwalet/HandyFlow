package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAccreditation;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAccreditationEvidence;
import za.co.handyflow.platform.trainingprovider.domain.model.TrainProvAuthority;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.dto.AccreditationDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvAuthorityFrameworkServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID ME = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Mock TrainProvAuthorityRepository authorities;
    @Mock TrainProvAccreditationRepository accreditations;
    @Mock TrainProvAccreditationScopeRepository scopes;
    @Mock TrainProvAccreditationEventRepository events;
    @Mock TrainProvAccreditationEvidenceRepository evidence;
    @Mock TrainProvReportObligationRepository obligations;
    @InjectMocks TrainProvAuthorityFrameworkService service;

    private TrainProvAuthority authority(String name, String type) {
        return TrainProvAuthority.create(TENANT, name, type, null);
    }

    private TrainProvAccreditation accreditation(TrainProvAuthority a, String title) {
        TrainProvAccreditation acc = TrainProvAccreditation.create(TENANT, a.getId(), title, null, ME);
        lenient().when(accreditations.findForTenant(TENANT, acc.getId())).thenReturn(Optional.of(acc));
        return acc;
    }

    @Test
    void listsAuthoritiesInTheAgreedOrderWithCurrentRecordsFirst() {
        TrainProvAuthority che = authority("CHE", "CHE");
        TrainProvAuthority saqa = authority("SAQA", "SAQA");
        TrainProvAuthority qcto = authority("QCTO", "QCTO");
        TrainProvAccreditation hist = accreditation(qcto, "Old");
        hist.setCurrency("HISTORICAL", "Replaced by the new registration", TODAY.minusDays(1), null, TODAY);
        TrainProvAccreditation cur = accreditation(qcto, "New");
        when(authorities.findAllForTenant(TENANT)).thenReturn(List.of(che, saqa, qcto));
        when(accreditations.findAllForTenant(TENANT, null, null)).thenReturn(List.of(hist, cur));
        when(evidence.allForTenant(TENANT)).thenReturn(List.of());
        when(obligations.search(TENANT, null, "ACTIVE")).thenReturn(List.of());
        List<FrameworkAuthority> out = service.overview(TENANT, TODAY);
        assertThat(out).extracting(FrameworkAuthority::name).containsExactly("QCTO", "SAQA", "CHE");
        assertThat(out.get(0).accreditations()).extracting(FrameworkRow::title).containsExactly("New", "Old");
        assertThat(out.get(0).accreditations().get(1).currency()).isEqualTo("HISTORICAL");
    }

    @Test
    void aRecordCannotBeReplacedByAnotherAuthoritysRecord() {
        TrainProvAuthority a = authority("QCTO", "QCTO");
        TrainProvAuthority b = authority("SAQA", "SAQA");
        TrainProvAccreditation old = accreditation(a, "Old");
        TrainProvAccreditation other = accreditation(b, "Other");
        assertThatThrownBy(() -> service.changeCurrency(TENANT, old.getId(),
                new CurrencyRequest("HISTORICAL", "Replaced by the new registration", TODAY.minusDays(1), other.getId()), ME, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("same authority");
        verify(accreditations, never()).save(any());
    }

    @Test
    void markingHistoricalIsRecordedInTheHistory() {
        TrainProvAuthority a = authority("QCTO", "QCTO");
        TrainProvAccreditation old = accreditation(a, "Old");
        service.changeCurrency(TENANT, old.getId(), new CurrencyRequest("HISTORICAL", "Replaced by the new registration", TODAY.minusDays(1), null), ME, TODAY);
        assertThat(old.getCurrency()).isEqualTo("HISTORICAL");
        verify(events).save(any());
    }

    @Test
    void aHistoricalRecordNeedsItsEndDate() {
        TrainProvAuthority a = authority("QCTO", "QCTO");
        TrainProvAccreditation old = accreditation(a, "Old");
        assertThatThrownBy(() -> service.changeCurrency(TENANT, old.getId(), new CurrencyRequest("HISTORICAL", "Replaced by the new registration", null, null), ME, TODAY))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("date");
    }

    @Test
    void evidenceStartsPendingAndASecondPersonVerifiesIt() {
        TrainProvAuthority a = authority("QCTO", "QCTO");
        TrainProvAccreditation acc = accreditation(a, "Acc");
        EvidenceView v = service.addEvidence(TENANT, acc.getId(), new EvidenceRequest("LETTER", "Approval letter", null, null, TODAY), ME, TODAY);
        assertThat(v.status()).isEqualTo("PENDING");
        TrainProvAccreditationEvidence e = TrainProvAccreditationEvidence.add(TENANT, acc.getId(), "LETTER", "Approval letter", null, null, TODAY, ME, TODAY);
        when(evidence.findForTenant(TENANT, e.getId())).thenReturn(Optional.of(e));
        assertThatThrownBy(() -> service.decideEvidence(TENANT, e.getId(), new EvidenceDecisionRequest("VERIFIED", null), ME))
                .isInstanceOf(HandyFlowException.class).hasMessageContaining("second person");
        assertThat(service.decideEvidence(TENANT, e.getId(), new EvidenceDecisionRequest("VERIFIED", null), OTHER).status()).isEqualTo("VERIFIED");
    }

    @Test
    void savesDatesAndSourceAndRefusesAnIncompleteSource() {
        TrainProvAuthority a = authority("QCTO", "QCTO");
        TrainProvAccreditation acc = accreditation(a, "Acc");
        assertThatThrownBy(() -> service.updateFramework(TENANT, acc.getId(), new FrameworkRequest(null, null, null, "Letter 22/3", null, null), ME, TODAY))
                .isInstanceOf(HandyFlowException.class);
        FrameworkRow row = service.updateFramework(TENANT, acc.getId(), new FrameworkRequest(TODAY, TODAY.plusMonths(6), "LETTER", "Letter 22/3", TODAY.minusDays(9), "p2"), ME, TODAY);
        assertThat(row.sourceType()).isEqualTo("LETTER");
        assertThat(row.reviewDueOn()).isEqualTo(TODAY.plusMonths(6));
    }
}
