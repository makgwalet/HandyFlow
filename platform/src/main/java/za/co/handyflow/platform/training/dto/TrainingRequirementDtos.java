package za.co.handyflow.platform.training.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Required training: what is asked of each role, who meets it, and the manager's view of a team. */
public final class TrainingRequirementDtos {

    private TrainingRequirementDtos() {}

    public record RequirementRequest(@NotNull UUID courseId, String jobTitle, String department) {}

    public record Requirement(UUID id, UUID courseId, String courseTitle, String jobTitle, String department, String appliesTo) {}

    /** One column of the matrix: a course that somebody is required to hold. */
    public record Column(UUID courseId, String courseTitle) {}

    /** A cell is null where the course is not required of that person. */
    public record Cell(String status, String label, LocalDate expiryDate) {}

    public record Row(UUID employeeId, String employeeNumber, String name, String jobTitle, String department, List<Cell> cells) {}

    public record Summary(int people, int required, int valid, int expiring, int expired, int missing, int booked) {}

    public record Matrix(List<Column> columns, List<Row> rows, Summary summary) {}

    /** One required course for one person. */
    public record Required(UUID courseId, String courseTitle, String status, String label, LocalDate expiryDate) {}

    public record Member(UUID employeeId, String name, String jobTitle, List<Required> required,
                         List<MyTrainingResponse.Certificate> certificates, List<MyTrainingResponse.Booking> bookings) {}

    public record Team(boolean linked, List<Member> members) {}

    /** A session an employee may book themselves onto. */
    public record OpenSession(UUID id, UUID courseId, String courseTitle, LocalDate startDate, LocalDate endDate, String venue,
                              Integer seatsLeft, boolean required) {}

    public record BookRequest(@NotNull UUID sessionId) {}
}
