package za.co.handyflow.platform.hr.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import za.co.handyflow.platform.hr.domain.model.HrCareerEvent;
import za.co.handyflow.platform.hr.domain.repository.HrCareerEventRepository;

/** Saves a {@link CareerChange} in the publisher's own transaction (a plain listener, not an after-commit one). */
@Component
@RequiredArgsConstructor
public class CareerRecorder {

    private final HrCareerEventRepository repo;

    @EventListener
    public void on(CareerChange c) {
        repo.save(HrCareerEvent.of(c.tenantId().getValue(), c.employeeId(), c.kind(), c.date(), null, null, c.jobTitle(), c.department(),
                cut(c.from(), 200), cut(c.to(), 200), cut(c.note(), 500), c.payRelated(), true, c.actor()));
    }

    private static String cut(String s, int max) { return s == null ? null : s.length() <= max ? s : s.substring(0, max); }
}
