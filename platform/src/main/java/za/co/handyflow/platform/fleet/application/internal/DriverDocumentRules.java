package za.co.handyflow.platform.fleet.application.internal;

import za.co.handyflow.platform.fleet.domain.model.DriverDocumentType;

import java.time.LocalDate;

/** What each kind of driver document needs before it is accepted. Pure, so it can be tested without Spring. */
final class DriverDocumentRules {

    private DriverDocumentRules() {}

    /** Throws IllegalArgumentException with a message fit to show the user. */
    static void check(DriverDocumentType type, String contentType, String title, LocalDate issueDate, LocalDate expiryDate) {
        if (type == DriverDocumentType.PHOTO && (contentType == null || !contentType.startsWith("image/"))) {
            throw new IllegalArgumentException("A driver photo must be a JPEG, PNG or WebP image");
        }
        if (type == DriverDocumentType.TRAINING_CERTIFICATE && (title == null || title.isBlank())) {
            throw new IllegalArgumentException("Name the course for a training certificate");
        }
        if (type == DriverDocumentType.MEDICAL_CERTIFICATE && expiryDate == null) {
            throw new IllegalArgumentException("A medical certificate needs an expiry date");
        }
        if (issueDate != null && expiryDate != null && expiryDate.isBefore(issueDate)) {
            throw new IllegalArgumentException("Expiry date cannot be before the issue date");
        }
    }
}
