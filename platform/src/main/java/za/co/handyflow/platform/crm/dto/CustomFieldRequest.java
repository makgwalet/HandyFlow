package za.co.handyflow.platform.crm.dto;

import za.co.handyflow.platform.crm.domain.model.CustomFieldType;

import java.util.List;

/** Creates or updates a field. type is used only when creating; options only for choice fields. */
public record CustomFieldRequest(String label, CustomFieldType type, Boolean required, List<String> options, Integer sortOrder, Boolean active) {}
