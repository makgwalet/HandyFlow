package za.co.handyflow.platform.clinic.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class GrowthDtos {
    private GrowthDtos() {}

    public record PointDto(BigDecimal ageMonths, BigDecimal l, BigDecimal m, BigDecimal s) {}
    public record CreateSetRequest(String measure, String sex, String title, String source, String sourceVersion,
                                   BigDecimal minAgeMonths, BigDecimal maxAgeMonths, List<PointDto> points) {}
    public record NoteRequest(String note) {}
    public record SetRow(UUID id, String measure, String sex, String title, String source, String sourceVersion,
                         BigDecimal minAgeMonths, BigDecimal maxAgeMonths, String status, int pointCount,
                         UUID createdBy, UUID reviewedBy, Instant reviewedAt, UUID approvedBy, Instant approvedAt) {}

    /** One measurement placed on the child's age. zScore and percentile are null unless an ACTIVE approved set covers the age. */
    public record Measured(UUID observationId, Instant takenAt, double ageMonths, BigDecimal value, Double zScore, Double percentile) {}
    public record CurvePoint(double ageMonths, List<Double> values) {}
    public record Curves(UUID setId, String source, String sourceVersion, List<Double> zLines, List<CurvePoint> points) {}
    public record MeasureChart(String code, String label, String unit, String banner, List<Measured> measurements, Curves curves) {}
    public record GrowthChart(UUID patientId, String sexAtBirth, Double currentAgeMonths, List<String> notes, List<MeasureChart> measures) {}
}
