package za.co.handyflow.platform.crm.dto;

/** One condition: a field (TYPE, STATUS, SOURCE, STAGE, OWNER, TAG, DEAL_VALUE, CREATED, UPDATED, EMAIL, PHONE), a comparison and a value. */
public record SegmentConditionDto(String field, String op, String value) {}
