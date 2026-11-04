package za.co.handyflow.platform.projects.application.internal;

import org.junit.jupiter.api.Test;
import za.co.handyflow.platform.projects.domain.model.Project;
import za.co.handyflow.platform.projects.dto.UpdateProjectRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** The activity log names exactly the project details an edit changes. */
class ProjectEditActivityTest {

    Project project() {
        Project p = Project.create(UUID.randomUUID(), "PRJ-0042", "Sandton Fit-out", "CONSTRUCTION", null, "Mokoena",
                LocalDate.of(2026, 8, 1), LocalDate.of(2027, 2, 28), new BigDecimal("2450000"), null);
        p.setContractValue(new BigDecimal("2900000.00"));
        return p;
    }

    UpdateProjectRequest req(String name, LocalDate end, BigDecimal contract, String site, String notes) {
        return new UpdateProjectRequest(name, null, null, end, null, contract, null, null, null, null, null, site,
                null, null, null, null, null, null, null, notes);
    }

    @Test
    void onlyRealChangesAreNamed() {
        Project p = project();
        assertEquals(List.of("end date", "site address"),
                ProjectService.changedFields(p, req("Sandton Fit-out", LocalDate.of(2027, 3, 31), new BigDecimal("2900000"), "12 Rivonia Rd", null)),
                "same name and an equal contract value (different scale) are not changes");
    }

    @Test
    void aFormThatResendsEverythingChangesNothing() {
        Project p = project();
        assertTrue(ProjectService.changedFields(p, req("Sandton Fit-out", LocalDate.of(2027, 2, 28), new BigDecimal("2900000.00"), null, null)).isEmpty());
        assertTrue(ProjectService.changedFields(p, req(null, null, null, null, "   ")).isEmpty(), "blank notes when there are none");
    }
}
