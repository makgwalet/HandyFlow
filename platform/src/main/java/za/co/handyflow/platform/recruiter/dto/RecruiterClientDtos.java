package za.co.handyflow.platform.recruiter.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RecruiterClientDtos {
    private RecruiterClientDtos() {}

    public record ClientRequest(String name, String industry, String location, String notes, String status) {}
    public record ContactRequest(String name, String email, String phone, String roleTitle, Boolean primary) {}
    public record JobClientRequest(UUID clientId) {}
    public record FeeRequest(BigDecimal fee) {}

    public record ClientRow(UUID id, String name, String industry, String location, String status, String primaryContact, long openJobs, long totalJobs, long placements) {}
    public record Contact(UUID id, String name, String email, String phone, String roleTitle, boolean primary) {}
    public record ClientJob(UUID id, String title, String status, long applications, long hires) {}
    public record ClientDetail(UUID id, String name, String industry, String location, String status, String notes, Instant createdAt,
                               List<Contact> contacts, List<ClientJob> jobs) {}
    public record JobClient(UUID jobId, UUID clientId, String clientName) {}

    public record Placement(UUID applicationId, String candidate, UUID jobId, String jobTitle, UUID clientId, String clientName,
                            Instant hiredAt, LocalDate startDate, BigDecimal fee, boolean onHr) {}
    public record Placements(int days, long count, BigDecimal feeTotal, long withoutFee, List<Placement> rows) {}
}
