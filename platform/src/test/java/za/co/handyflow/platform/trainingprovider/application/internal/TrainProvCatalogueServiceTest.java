package za.co.handyflow.platform.trainingprovider.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.dto.AccreditationDtos.CoverageView;
import za.co.handyflow.platform.trainingprovider.dto.CatalogueDtos.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainProvCatalogueServiceTest {

    private static final TenantId TENANT = TenantId.of(UUID.fromString("00000000-0000-0000-0000-0000000000a1"));
    private static final UUID MAKER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID CHECKER = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 25);

    @Mock TrainProvQualificationRepository qualifications;
    @Mock TrainProvQualificationVersionRepository qualVersions;
    @Mock TrainProvProgrammeRepository programmes;
    @Mock TrainProvProgrammeVersionRepository versions;
    @Mock TrainProvProgrammeModuleRepository modules;
    @Mock TrainProvProgrammeOfferingRepository offerings;
    @Mock TrainProvDeliverySiteRepository sites;
    @Mock TrainProvAuthorityRepository authorities;
    @Mock TrainProvCourseRepository courses;
    @Mock TrainProvAccreditationService accreditation;
    @InjectMocks TrainProvCatalogueService service;

    private TrainProvProgramme accreditedProgramme(boolean active) {
        TrainProvProgramme p = TrainProvProgramme.create(TENANT, "P1", "TEST programme", "SKILLS_PROGRAMME", "ACCREDITED",
                "CLASSROOM", UUID.randomUUID(), null, null, MAKER);
        if (active) {
            p.activate();
        }
        return p;
    }

    private TrainProvDeliverySite site() {
        return TrainProvDeliverySite.create(TENANT, "TEST campus", "CAMPUS", null, null, null, null);
    }

    @Test
    void accreditedProgrammeIsNotOfferedWhereNoApprovalCoversTheSite() {
        TrainProvProgramme p = accreditedProgramme(true);
        TrainProvDeliverySite s = site();
        when(programmes.findForTenant(TENANT, p.getId())).thenReturn(Optional.of(p));
        when(sites.findForTenant(TENANT, s.getId())).thenReturn(Optional.of(s));
        when(accreditation.programmeCoverage(TENANT, p.getId(), null, s.getId(), TODAY))
                .thenReturn(new CoverageView(p.getId(), s.getId(), TODAY, "NO_SCOPE", false, "No accreditation scope has been recorded"));

        assertThatThrownBy(() -> service.addOffering(TENANT, p.getId(), new OfferingRequest(s.getId(), null), MAKER, TODAY))
                .isInstanceOf(HandyFlowException.class);
        verify(offerings, never()).save(any());
    }

    @Test
    void accreditedProgrammeIsOfferedWhereAnApprovalCoversTheSite() {
        TrainProvProgramme p = accreditedProgramme(true);
        TrainProvDeliverySite s = site();
        when(programmes.findForTenant(TENANT, p.getId())).thenReturn(Optional.of(p));
        when(sites.findForTenant(TENANT, s.getId())).thenReturn(Optional.of(s));
        when(accreditation.programmeCoverage(TENANT, p.getId(), null, s.getId(), TODAY))
                .thenReturn(new CoverageView(p.getId(), s.getId(), TODAY, "COVERED", true, "Covered by a current approval"));

        OfferingView v = service.addOffering(TENANT, p.getId(), new OfferingRequest(s.getId(), null), MAKER, TODAY);

        assertThat(v.status()).isEqualTo("ACTIVE");
        verify(offerings).save(any(TrainProvProgrammeOffering.class));
    }

    @Test
    void nonAccreditedProgrammeNeedsNoApprovalToBeOffered() {
        TrainProvProgramme p = TrainProvProgramme.create(TENANT, "C1", "TEST short course", "NON_ACCREDITED_COURSE",
                "NON_ACCREDITED", "CLASSROOM", null, null, null, MAKER);
        p.activate();
        TrainProvDeliverySite s = site();
        when(programmes.findForTenant(TENANT, p.getId())).thenReturn(Optional.of(p));
        when(sites.findForTenant(TENANT, s.getId())).thenReturn(Optional.of(s));

        service.addOffering(TENANT, p.getId(), new OfferingRequest(s.getId(), null), MAKER, TODAY);

        verify(accreditation, never()).programmeCoverage(any(), any(), any(), any(), any());
        verify(offerings).save(any(TrainProvProgrammeOffering.class));
    }

    @Test
    void aDraftProgrammeCannotBeOffered() {
        TrainProvProgramme p = accreditedProgramme(false);
        when(programmes.findForTenant(TENANT, p.getId())).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.addOffering(TENANT, p.getId(), new OfferingRequest(UUID.randomUUID(), null), MAKER, TODAY))
                .isInstanceOf(HandyFlowException.class);
    }

    @Test
    void aProgrammeCannotBeActivatedWithoutAnApprovedVersion() {
        TrainProvProgramme p = accreditedProgramme(false);
        when(programmes.findForTenant(TENANT, p.getId())).thenReturn(Optional.of(p));
        when(versions.findApproved(TENANT, p.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activateProgramme(TENANT, p.getId(), TODAY)).isInstanceOf(HandyFlowException.class);
        assertThat(p.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void aVersionWithNoModulesCannotBeApproved() {
        TrainProvProgramme p = TrainProvProgramme.create(TENANT, "C1", "TEST short course", "NON_ACCREDITED_COURSE",
                "NON_ACCREDITED", "CLASSROOM", null, null, null, MAKER);
        TrainProvProgrammeVersion v = TrainProvProgrammeVersion.draft(TENANT, p.getId(), 1, MAKER);
        when(versions.findForTenant(TENANT, v.getId())).thenReturn(Optional.of(v));
        when(programmes.findForTenant(TENANT, p.getId())).thenReturn(Optional.of(p));
        when(modules.findForVersion(TENANT, v.getId())).thenReturn(List.of());

        assertThatThrownBy(() -> service.approveVersion(TENANT, v.getId(), null, CHECKER)).isInstanceOf(HandyFlowException.class);
        assertThat(v.getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void approvingANewVersionSupersedesTheOldOneAndKeepsIt() {
        TrainProvProgramme p = TrainProvProgramme.create(TENANT, "C1", "TEST short course", "NON_ACCREDITED_COURSE",
                "NON_ACCREDITED", "CLASSROOM", null, null, null, MAKER);
        TrainProvProgrammeVersion old = TrainProvProgrammeVersion.draft(TENANT, p.getId(), 1, MAKER);
        old.approve(CHECKER, null);
        TrainProvProgrammeVersion v = TrainProvProgrammeVersion.draft(TENANT, p.getId(), 2, MAKER);
        when(versions.findForTenant(TENANT, v.getId())).thenReturn(Optional.of(v));
        when(programmes.findForTenant(TENANT, p.getId())).thenReturn(Optional.of(p));
        when(modules.findForVersion(TENANT, v.getId())).thenReturn(List.of(mock(TrainProvProgrammeModule.class)));
        when(versions.findApproved(TENANT, p.getId())).thenReturn(Optional.of(old));

        service.approveVersion(TENANT, v.getId(), null, CHECKER);

        assertThat(old.getStatus()).isEqualTo("SUPERSEDED");
        assertThat(v.getStatus()).isEqualTo("APPROVED");
        verify(versions).saveAndFlush(old);
    }

    @Test
    void anApprovedVersionIsFrozen() {
        TrainProvProgrammeVersion v = TrainProvProgrammeVersion.draft(TENANT, UUID.randomUUID(), 1, MAKER);
        v.approve(CHECKER, null);
        when(versions.findForTenant(TENANT, v.getId())).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.addModule(TENANT, v.getId(), new ModuleRequest("M", "KNOWLEDGE", null, null, null, null)))
                .isInstanceOf(HandyFlowException.class);
        verify(modules, never()).save(any());
    }

    @Test
    void aQualificationStillUsedByProgrammesCannotBeRetired() {
        TrainProvQualification q = TrainProvQualification.create(TENANT, "TEST qualification", "QUALIFICATION", null, null, null, null, null, null, MAKER);
        when(qualifications.findForTenant(TENANT, q.getId())).thenReturn(Optional.of(q));
        when(programmes.countLiveForQualification(TENANT, q.getId())).thenReturn(1L);

        assertThatThrownBy(() -> service.retireQualification(TENANT, q.getId(), "Replaced by a newer one", MAKER))
                .isInstanceOf(HandyFlowException.class);
        assertThat(q.getStatus()).isEqualTo("ACTIVE");
    }
}
