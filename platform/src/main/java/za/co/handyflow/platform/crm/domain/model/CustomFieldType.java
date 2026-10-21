package za.co.handyflow.platform.crm.domain.model;

/** What kind of value a custom field holds. */
public enum CustomFieldType {
    TEXT,
    NUMBER,
    DATE,
    /** One value from a fixed list. */
    CHOICE,
    YES_NO,
    /** Any number of values from a fixed list. Stored joined with "|". */
    MULTI_CHOICE,
    /** An email address. */
    EMAIL,
    /** A web address starting http:// or https://. */
    URL;

    /** Whether the field is defined with a list of options. */
    public boolean hasOptions() { return this == CHOICE || this == MULTI_CHOICE; }
}
