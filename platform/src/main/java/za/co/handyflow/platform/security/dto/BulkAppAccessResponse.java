package za.co.handyflow.platform.security.dto;

import java.util.List;
import java.util.UUID;

public record BulkAppAccessResponse(int provisioned, int skipped, int failed, boolean moreRemaining,
                                    List<Item> results) {

    /** temporaryPin is present only for PROVISIONED rows and is shown once. */
    public record Item(UUID guardId, String fullName, String employeeCode, String phone,
                       String outcome, String temporaryPin, String message) {}
}
