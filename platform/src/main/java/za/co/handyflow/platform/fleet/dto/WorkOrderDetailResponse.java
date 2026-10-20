package za.co.handyflow.platform.fleet.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record WorkOrderDetailResponse(
        WorkOrderResponse summary,
        String description,
        UUID issueId,
        UUID accidentId,
        Integer odometerIn,
        Instant vehicleOffRoadFrom,

        // quotation & approval
        String quotationRef,
        LocalDate quotationDate,
        LocalDate quotationValidUntil,
        String quotationNotes,
        UUID approvedBy,
        Instant approvedAt,
        String approvalNote,
        String rejectionReason,
        String holdReason,
        String cancelReason,

        // actual cost
        List<WorkOrderLineResponse> lines,
        BigDecimal labourTotal,
        BigDecimal partsTotal,
        BigDecimal subletTotal,
        BigDecimal otherTotal,
        BigDecimal varianceToQuote,     // actual − quoted; null when there is no quotation
        boolean overQuote,

        // downtime
        Double downtimeHours,           // null until the vehicle is off the road / the work starts
        boolean downtimeOngoing,

        // completion
        Integer completionOdometer,
        String invoiceRef,
        String completionNotes,
        Integer nextServiceKm,
        UUID serviceRecordId,

        List<WorkOrderAttachmentResponse> attachments
) {}