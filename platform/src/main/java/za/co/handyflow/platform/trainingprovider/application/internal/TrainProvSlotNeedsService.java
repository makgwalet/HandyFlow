package za.co.handyflow.platform.trainingprovider.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.trainingprovider.domain.model.*;
import za.co.handyflow.platform.trainingprovider.domain.repository.*;
import za.co.handyflow.platform.trainingprovider.domain.rules.ProgrammeNeedsRules;
import za.co.handyflow.platform.trainingprovider.dto.ProgrammeNeedsDtos.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * For each scheduled session of a cohort, how what is booked compares with what the programme version lists as needed. A prompt to check the bookings.
 * It never says a session meets or fails any standard, and it changes nothing.
 */
@Service
@RequiredArgsConstructor
public class TrainProvSlotNeedsService {

    private final TrainProvCohortRepository cohorts;
    private final TrainProvCohortMemberRepository members;
    private final TrainProvProgrammeResourceNeedRepository needs;
    private final TrainProvTimetableSlotRepository slots;
    private final TrainProvSlotResourceRepository bookings;
    private final TrainProvSiteResourceRepository resources;

    @Transactional(readOnly = true)
    public CohortSlotNeeds forCohort(TenantId t, UUID cohortId, LocalDate today) {
        TrainProvCohort c = cohorts.findForTenant(t, cohortId).orElseThrow(() -> new ResourceNotFoundException("TrainProvCohort", cohortId.toString()));
        long learners = members.countActive(t, cohortId);
        List<TrainProvProgrammeResourceNeed> live = needs.live(t, c.getProgrammeVersionId());
        Map<UUID, TrainProvSiteResource> byId = new HashMap<>();
        for (TrainProvSiteResource r : resources.search(t, c.getSiteId(), null, null)) {
            byId.put(r.getId(), r);
        }
        List<SlotNeeds> out = new ArrayList<>();
        if (!live.isEmpty()) {
            for (TrainProvTimetableSlot s : slots.findForCohort(t, cohortId)) {
                if (!"SCHEDULED".equals(s.getStatus()) || s.getSlotDate().isBefore(today)) {
                    continue;
                }
                List<TrainProvSlotResource> booked = bookings.forSlot(t, s.getId()).stream().filter(b -> "BOOKED".equals(b.getStatus())).toList();
                List<SlotNeedRow> rows = new ArrayList<>();
                int notCovered = 0;
                for (TrainProvProgrammeResourceNeed n : live) {
                    int held = 0;
                    for (TrainProvSlotResource b : booked) {
                        TrainProvSiteResource r = byId.get(b.getResourceId());
                        if (r != null && r.getResourceType().equals(n.getResourceType()) && ProgrammeNeedsRules.matches(r.getName(), n.getMatchText())) {
                            held += b.getQuantity();
                        }
                    }
                    int required = ProgrammeNeedsRules.required(n.getQuantity(), n.getPerLearners(), learners);
                    String status = ProgrammeNeedsRules.readiness(required, held);
                    if (!ProgrammeNeedsRules.ENOUGH.equals(status)) {
                        notCovered++;
                    }
                    rows.add(new SlotNeedRow(n.getId(), n.getDescription(), required, held, status, ProgrammeNeedsRules.slotNote(status, required, held)));
                }
                out.add(new SlotNeeds(s.getId(), notCovered, rows));
            }
        }
        return new CohortSlotNeeds(c.getId(), "A comparison of each session's bookings with the programme's own list. It is a prompt to check and does not show that any session meets a standard.", out);
    }
}
