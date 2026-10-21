package za.co.handyflow.platform.crm.domain.model;

/** What kind of value a custom field holds. */
public enum CustomFieldType {
    TEXT,
    NUMBER,
    DATE,
    /** One value from a fixed list. */
    CHOICE,
    YES_NO
}
