package za.co.handyflow.platform.security.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who would still be issued a firearm if strict competency checking were switched on.
 * Only guards with some firearm competency on record appear (guards who carry no firearms are not listed).
 */
public record CompetencyReadinessResponse(boolean strictEnabled, boolean safeToSwitch, int blockedByStrict,
                                          Map<String, Integer> counts, List<Row> guards) {
    public record Row(UUID guardId, String fullName, String employeeCode, String status,
                      List<String> coveredCategories, String hint) {}
}
