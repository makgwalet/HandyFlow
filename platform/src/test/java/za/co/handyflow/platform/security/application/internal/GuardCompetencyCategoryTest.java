package za.co.handyflow.platform.security.application.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import za.co.handyflow.platform.evidence.application.EvidenceFacade;
import za.co.handyflow.platform.security.domain.repository.GuardCompetencyRepository;
import za.co.handyflow.platform.security.dto.SaveGuardCompetencyRequest;
import za.co.handyflow.platform.shared.HandyFlowException;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Mockito test, not run in the authoring sandbox (no Maven jars). Mirrors GuardCompetencyServiceTest's setup. */
@ExtendWith(MockitoExtension.class)
class GuardCompetencyCategoryTest {
    @Mock private GuardCompetencyRepository repository;
    @Mock private GuardService guardService;
    @Mock private EvidenceFacade evidenceFacade;

    private static final TenantId TENANT = TenantId.generate();
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 7);
    private final UUID guardId = UUID.randomUUID();

    private GuardCompetencyService service() { return new GuardCompetencyService(repository, guardService, evidenceFacade); }

    private static SaveGuardCompetencyRequest firearm(String category, LocalDate expiry) {
        return new SaveGuardCompetencyRequest("FIREARM_COMPETENCY", null, "SAPS", TODAY.minusMonths(6), expiry, "C-1", true, null, category);
    }

    @Test
    void firearmCompetencyNeedsACategory() {
        HandyFlowException e = assertThrows(HandyFlowException.class,
                () -> service().create(TENANT, guardId, firearm(null, TODAY.plusYears(1)), UUID.randomUUID(), TODAY));
        assertEquals("FIREARM_CATEGORY_REQUIRED", e.getErrorCode());
    }

    @Test
    void firearmCompetencyNeedsAnExpiry() {
        HandyFlowException e = assertThrows(HandyFlowException.class,
                () -> service().create(TENANT, guardId, firearm("HANDGUN", null), UUID.randomUUID(), TODAY));
        assertEquals("EXPIRY_REQUIRED", e.getErrorCode());
    }

    @Test
    void unknownCategoryRefused() {
        HandyFlowException e = assertThrows(HandyFlowException.class,
                () -> service().create(TENANT, guardId, firearm("CANNON", TODAY.plusYears(1)), UUID.randomUUID(), TODAY));
        assertEquals("INVALID_FIREARM_CATEGORY", e.getErrorCode());
    }

    @Test
    void categoryIsSavedAndReturned() {
        var r = service().create(TENANT, guardId, firearm(" rifle ", TODAY.plusYears(1)), UUID.randomUUID(), TODAY);
        assertEquals("RIFLE", r.firearmCategory());
    }

    @Test
    void otherTypesIgnoreTheCategory() {
        var req = new SaveGuardCompetencyRequest("FIRST_AID", null, "St John", TODAY.minusMonths(1), TODAY.plusYears(1), "R", false, null, "RIFLE");
        assertNull(service().create(TENANT, guardId, req, UUID.randomUUID(), TODAY).firearmCategory());
    }
}
