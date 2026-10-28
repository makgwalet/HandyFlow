package za.co.handyflow.platform.training.application.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.handyflow.platform.hr.EmployeeSummary;
import za.co.handyflow.platform.hr.application.HrFacade;
import za.co.handyflow.platform.shared.ResourceNotFoundException;
import za.co.handyflow.platform.shared.TenantId;
import za.co.handyflow.platform.training.domain.model.TrainingCourse;
import za.co.handyflow.platform.training.domain.model.TrainingRequirement;
import za.co.handyflow.platform.training.domain.model.TrainingRequirementRules;
import za.co.handyflow.platform.training.domain.repository.TrainingCourseRepository;
import za.co.handyflow.platform.training.domain.repository.TrainingRequirementRepository;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.Cell;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.Column;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.Matrix;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.Requirement;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.Required;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.Row;
import za.co.handyflow.platform.training.dto.TrainingRequirementDtos.Summary;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** The required-training matrix: what each role must hold, and who does. */
@Service
@RequiredArgsConstructor
public class TrainingRequirementService {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final TrainingRequirementRepository requirementRepository;
    private final TrainingCourseRepository courseRepository;
    private final HrFacade hrFacade;
    private final TrainingPicture picture;

    @Transactional(readOnly = true)
    public List<Requirement> list(TenantId tenant) {
        List<TrainingRequirement> all = requirementRepository.findAllForTenant(tenant);
        var titles = courseRepository.findAllById(all.stream().map(TrainingRequirement::getCourseId).distinct().toList()).stream()
                .filter(c -> tenant.getValue().equals(c.getTenantId())).collect(java.util.stream.Collectors.toMap(TrainingCourse::getId, TrainingCourse::getTitle));
        return all.stream().map(r -> new Requirement(r.getId(), r.getCourseId(), titles.getOrDefault(r.getCourseId(), "Course"), r.getJobTitle(), r.getDepartment(),
                TrainingRequirementRules.appliesTo(r.getJobTitle(), r.getDepartment()))).toList();
    }

    @Transactional
    public Requirement add(TenantId tenant, UUID courseId, String jobTitle, String department, UUID by) {
        TrainingCourse course = courseRepository.findActiveById(tenant, courseId).orElse(null);
        String problem = TrainingRequirementRules.problem(course != null, course != null && course.isActive(), course != null && course.isCertificationOffered(), jobTitle, department);
        if (problem != null) throw new IllegalArgumentException(problem);
        String title = TrainingRequirementRules.norm(jobTitle), dept = TrainingRequirementRules.norm(department);
        boolean twin = requirementRepository.findAllForTenant(tenant).stream().anyMatch(r -> courseId.equals(r.getCourseId())
                && Objects.equals(title, TrainingRequirementRules.norm(r.getJobTitle())) && Objects.equals(dept, TrainingRequirementRules.norm(r.getDepartment())));
        if (twin) throw new IllegalStateException("That requirement is already set.");
        TrainingRequirement saved = requirementRepository.save(TrainingRequirement.create(tenant, courseId, jobTitle, department, by));
        return new Requirement(saved.getId(), courseId, course.getTitle(), saved.getJobTitle(), saved.getDepartment(),
                TrainingRequirementRules.appliesTo(saved.getJobTitle(), saved.getDepartment()));
    }

    @Transactional
    public void remove(TenantId tenant, UUID id) {
        TrainingRequirement r = requirementRepository.findByTenantAndId(tenant, id)
                .orElseThrow(() -> new ResourceNotFoundException("TrainingRequirement", id.toString()));
        requirementRepository.delete(r);
    }

    /** Everyone not yet terminated to whom something is required, against every course that is required of anyone. */
    @Transactional(readOnly = true)
    public Matrix matrix(TenantId tenant) {
        LocalDate today = LocalDate.now(SAST);
        List<EmployeeSummary> employees = hrFacade.activeEmployees(tenant);
        TrainingPicture.Picture pic = picture.of(tenant, employees, today);

        List<Column> columns = pic.requiredCourses().stream().map(c -> new Column(c, pic.courseTitles().get(c)))
                .sorted(Comparator.comparing(Column::courseTitle, String.CASE_INSENSITIVE_ORDER)).toList();
        List<Row> rows = new ArrayList<>();
        int valid = 0, expiring = 0, expired = 0, missing = 0, booked = 0;
        for (EmployeeSummary e : employees.stream().sorted(Comparator.comparing(EmployeeSummary::fullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))).toList()) {
            var required = pic.people().get(e.id()).required();
            if (required.isEmpty()) continue;
            List<Cell> cells = new ArrayList<>();
            for (Column c : columns) {
                Required r = required.get(c.courseId());
                if (r == null) { cells.add(null); continue; }
                cells.add(new Cell(r.status(), r.label(), r.expiryDate()));
                switch (r.status()) {
                    case TrainingRequirementRules.OK -> valid++;
                    case TrainingRequirementRules.EXPIRING -> expiring++;
                    case TrainingRequirementRules.EXPIRED -> expired++;
                    case TrainingRequirementRules.MISSING -> missing++;
                    default -> booked++;
                }
            }
            rows.add(new Row(e.id(), e.employeeNumber(), e.fullName(), e.jobTitle(), e.department(), cells));
        }
        return new Matrix(columns, rows, new Summary(rows.size(), valid + expiring + expired + missing + booked, valid, expiring, expired, missing, booked));
    }
}
