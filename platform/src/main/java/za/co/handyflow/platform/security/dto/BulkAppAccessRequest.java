package za.co.handyflow.platform.security.dto;

import java.util.List;
import java.util.UUID;

/**
 * guardIds null or empty = every active guard in the tenant (up to 100 per request: pass the ids to continue).
 * resetExisting = also re-issue a PIN to guards that already have app access (invalidates their current PIN).
 */
public record BulkAppAccessRequest(List<UUID> guardIds, boolean resetExisting) {}
