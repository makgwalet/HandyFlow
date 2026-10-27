package za.co.handyflow.platform.facilities.application.internal;

import za.co.handyflow.platform.facilities.domain.model.FacilityComplianceCertificate;
import za.co.handyflow.platform.facilities.domain.model.FacilityCorrectiveAction;
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
        List<Item> items = new ArrayList<>();

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

        for (FacilityComplianceCertificate c : certificates) {
            if ("REVOKED".equals(c.getStatus())) continue;
            long daysLeft = ChronoUnit.DAYS.between(today, c.getExpiryDate());
            String label = c.getCertificateType() + (c.getCertificateNumber() != null ? " " + c.getCertificateNumber() : "");
            if (daysLeft < 0) {
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
