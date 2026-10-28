package za.co.handyflow.platform.training.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.Member;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.Team;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A manager's view of the training of the people who report to them directly. Who "my team" is comes from the signed-in login's own employee
 * record; nothing takes an employee or manager id from the request, so nobody can ask for someone else's team.
 */
@Service
@RequiredArgsConstructor
public class TrainingTeamService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final HrFacade hrFacade;
    private final TrainingPicture picture;

    @Transactional(readOnly = true)
    public Team team(TenantId tenant, UUID userId) {
        EmployeeSummary me = hrFacade.findEmployeeByUserId(tenant, userId).orElse(null);
        if (me == null) return new Team(false, List.of());
        List<EmployeeSummary> reports = hrFacade.directReports(tenant, me.id()).stream()
                .sorted(Comparator.comparing(EmployeeSummary::fullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).toList();
        TrainingPicture.Picture pic = picture.of(tenant, reports, LocalDate.now(SAST));
        List<Member> members = reports.stream().map(e -> {
            TrainingPicture.Person p = pic.people().get(e.id());
            return new Member(e.id(), e.fullName(), e.jobTitle(), List.copyOf(p.required().values()), p.certificates(), p.bookings());
        }).toList();
        return new Team(true, members);
    }
}
