package za.co.handyflow.platform.facilities.application.internal;

import za.co.handyflow.platform.facilities.domain.model.FacilityComplianceCertificate;
import za.co.handyflow.platform.facilities.domain.model.FacilityCorrectiveAction;
import za.co.handyflow.platform.facilities.domain.model.FacilityInspectionSchedule;
import za.co.handyflow.platform.facilities.domain.model.FacilityTechnicianLicence;
import za.co.handyflow.platform.facilities.domain.model.FacilityVendorDocument;
import za.co.handyflow.platform.facilities.dto.ActionCentreResponse;
import za.co.handyflow.platform.facilities.dto.ActionCentreResponse.Item;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Pure ranking logic behind the action centre — no persistence, no clock. */
public final class FacilityActionCentre {

    public static final int URGENT_EXPIRY_DAYS = 14;
    public static final int SEVERELY_OVERDUE_DAYS = 7;
    public static final int MAX_ITEMS = 200;

    private static final List<String> ORDER = List.of("CRITICAL", "HIGH", "MEDIUM");

    private FacilityActionCentre() {}

    public static ActionCentreResponse build(Collection<FacilityComplianceCertificate> certificates,
                                             Collection<FacilityCorrectiveAction> unfinishedActions,
                                             LocalDate today, int warningDays) {
        return build(certificates, unfinishedActions, List.of(), Map.of(), today, warningDays);
    }

    /**
     * @param vendorDocuments VALID vendor documents that have lapsed or expire soon (any type)
     * @param vendorNames     vendor id -> name, for the item titles
     */
    public static ActionCentreResponse build(Collection<FacilityComplianceCertificate> certificates,
                                             Collection<FacilityCorrectiveAction> unfinishedActions,
                                             Collection<FacilityVendorDocument> vendorDocuments,
                                             Map<UUID, String> vendorNames, LocalDate today, int warningDays) {
        return build(certificates, unfinishedActions, vendorDocuments, vendorNames, List.of(), Map.of(), today, warningDays);
    }

    /**
     * @param overdueInspections active recurring inspections already past due
     * @param inspectionLabels   schedule id -> "Checklist at Site", for the item titles
     */
    public static ActionCentreResponse build(Collection<FacilityComplianceCertificate> certificates,
                                             Collection<FacilityCorrectiveAction> unfinishedActions,
                                             Collection<FacilityVendorDocument> vendorDocuments,
                                             Map<UUID, String> vendorNames,
                                             Collection<FacilityInspectionSchedule> overdueInspections,
                                             Map<UUID, String> inspectionLabels, LocalDate today, int warningDays) {
        return build(certificates, unfinishedActions, vendorDocuments, vendorNames, overdueInspections, inspectionLabels,
                List.of(), Map.of(), today, warningDays);
    }

    /**
     * @param technicianLicences VALID licences that have lapsed or expire soon (others are ignored)
     * @param technicianNames    technician id -> name, for the item titles
     */
    public static ActionCentreResponse build(Collection<FacilityComplianceCertificate> certificates,
                                             Collection<FacilityCorrectiveAction> unfinishedActions,
                                             Collection<FacilityVendorDocument> vendorDocuments,
                                             Map<UUID, String> vendorNames,
                                             Collection<FacilityInspectionSchedule> overdueInspections,
                                             Map<UUID, String> inspectionLabels,
                                             Collection<FacilityTechnicianLicence> technicianLicences,
                                             Map<UUID, String> technicianNames, LocalDate today, int warningDays) {
        List<Item> items = new ArrayList<>();

        for (FacilityTechnicianLicence l : technicianLicences) {
            if (!"VALID".equals(l.getStatus()) || l.getExpiryDate() == null) continue;
            long daysLeft = ChronoUnit.DAYS.between(today, l.getExpiryDate());
            String who = technicianNames.getOrDefault(l.getTechnicianId(), "Technician");
            if (daysLeft < 0) {
                items.add(new Item("HIGH", "TECHNICIAN_LICENCE_LAPSED", null, null, who + ": " + l.getLicenceType() + " licence lapsed",
                        "Expired " + (-daysLeft) + " day(s) ago on " + l.getExpiryDate(), l.getExpiryDate(), "TECHNICIAN", l.getTechnicianId()));
            } else if (daysLeft <= warningDays) {
                items.add(new Item(daysLeft <= URGENT_EXPIRY_DAYS ? "HIGH" : "MEDIUM", "TECHNICIAN_LICENCE_EXPIRING", null, null,
                        who + ": " + l.getLicenceType() + " licence expiring",
                        "Expires in " + daysLeft + " day(s) on " + l.getExpiryDate(), l.getExpiryDate(), "TECHNICIAN", l.getTechnicianId()));
            }
        }

        for (FacilityInspectionSchedule s : overdueInspections) {
            if (!s.isOverdue(today)) continue;
            long late = ChronoUnit.DAYS.between(s.getNextDueDate(), today);
            items.add(new Item(late > SEVERELY_OVERDUE_DAYS ? "CRITICAL" : "HIGH", "INSPECTION_OVERDUE", s.getSiteId(), s.getAssetId(),
                    "Inspection overdue: " + inspectionLabels.getOrDefault(s.getId(), "Recurring inspection"),
                    "Was due " + s.getNextDueDate() + " (" + late + " day(s) ago); every " + s.getFrequencyDays() + " days",
                    s.getNextDueDate(), "INSPECTION_SCHEDULE", s.getId()));
        }

        for (FacilityVendorDocument d : vendorDocuments) {
            if (!"VALID".equals(d.getStatus()) || d.getExpiryDate() == null) continue;
            long daysLeft = ChronoUnit.DAYS.between(today, d.getExpiryDate());
            String vendor = vendorNames.getOrDefault(d.getVendorId(), "Vendor");
            boolean required = FacilityVendorCompliance.REQUIRED.contains(d.getDocType());
            if (daysLeft < 0) {
                items.add(new Item(required ? "CRITICAL" : "HIGH", "VENDOR_DOCUMENT_LAPSED", null, null,
                        vendor + ": " + d.getDocType() + " lapsed",
                        "Expired " + (-daysLeft) + " day(s) ago on " + d.getExpiryDate() + (required ? " — vendor cannot be assigned work" : ""),
                        d.getExpiryDate(), "VENDOR", d.getVendorId()));
            } else if (daysLeft <= warningDays) {
                items.add(new Item(daysLeft <= URGENT_EXPIRY_DAYS ? "HIGH" : "MEDIUM", "VENDOR_DOCUMENT_EXPIRING", null, null,
                        vendor + ": " + d.getDocType() + " expiring",
                        "Expires in " + daysLeft + " day(s) on " + d.getExpiryDate(), d.getExpiryDate(), "VENDOR", d.getVendorId()));
            }
        }

        // A lapsed certificate that already has an open corrective action shows up as that action, not twice.
        Set<UUID> certificatesWithAction = new HashSet<>();
        for (FacilityCorrectiveAction a : unfinishedActions) {
            if (!a.isTerminal() && "CERTIFICATE".equals(a.getSourceType()) && a.getSourceId() != null) certificatesWithAction.add(a.getSourceId());
        }

        for (FacilityComplianceCertificate c : certificates) {
            if ("REVOKED".equals(c.getStatus())) continue;
            long daysLeft = ChronoUnit.DAYS.between(today, c.getExpiryDate());
            String label = c.getCertificateType() + (c.getCertificateNumber() != null ? " " + c.getCertificateNumber() : "");
            if (daysLeft < 0) {
                if (certificatesWithAction.contains(c.getId())) continue;
                items.add(new Item("CRITICAL", "CERTIFICATE_LAPSED", c.getSiteId(), c.getAssetId(),
                        "Certificate lapsed: " + label, "Expired " + (-daysLeft) + " day(s) ago on " + c.getExpiryDate(),
                        c.getExpiryDate(), "CERTIFICATE", c.getId()));
            } else if (daysLeft <= warningDays) {
                items.add(new Item(daysLeft <= URGENT_EXPIRY_DAYS ? "HIGH" : "MEDIUM", "CERTIFICATE_EXPIRING",
                        c.getSiteId(), c.getAssetId(), "Certificate expiring: " + label,
                        "Expires in " + daysLeft + " day(s) on " + c.getExpiryDate(), c.getExpiryDate(), "CERTIFICATE", c.getId()));
            }
        }

        for (FacilityCorrectiveAction a : unfinishedActions) {
            if (a.isTerminal()) continue;
            long overdueDays = a.isOverdue(today) ? ChronoUnit.DAYS.between(a.getDueDate(), today) : 0;
            String severity;
            if (a.isCritical() || overdueDays > SEVERELY_OVERDUE_DAYS) severity = "CRITICAL";
            else if (overdueDays > 0) severity = "HIGH";
            else continue; // on schedule and not critical: not an alert
            String detail = (overdueDays > 0 ? "Overdue by " + overdueDays + " day(s), was due " : "Due ") + a.getDueDate()
                    + " — " + a.getStatus();
            items.add(new Item(severity, overdueDays > 0 ? "ACTION_OVERDUE" : "ACTION_CRITICAL_OPEN", a.getSiteId(), a.getAssetId(),
                    a.getDescription(), detail, a.getDueDate(), "CORRECTIVE_ACTION", a.getId()));
        }

        items.sort(Comparator.<Item>comparingInt(i -> ORDER.indexOf(i.severity()))
                .thenComparing(Item::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Item::title));

        long critical = items.stream().filter(i -> "CRITICAL".equals(i.severity())).count();
        long high = items.stream().filter(i -> "HIGH".equals(i.severity())).count();
        long medium = items.size() - critical - high;
        List<Item> shown = items.size() > MAX_ITEMS ? new ArrayList<>(items.subList(0, MAX_ITEMS)) : items;
        return new ActionCentreResponse(critical, high, medium, shown);
    }
}
