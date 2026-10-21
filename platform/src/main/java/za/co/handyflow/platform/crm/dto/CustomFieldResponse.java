package za.co.handyflow.platform.crm.dto;

import za.co.handyflow.platform.crm.domain.model.CustomFieldType;

import java.util.List;
import java.util.UUID;

public record CustomFieldResponse(UUID id, String key, String label, CustomFieldType type, boolean required, List<String> options, int sortOrder, boolean active) {}
