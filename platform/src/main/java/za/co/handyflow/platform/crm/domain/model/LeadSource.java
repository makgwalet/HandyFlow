package za.co.handyflow.platform.crm.domain.model;

/** Where a lead or customer first came from. Optional; used for the "which sources win" report. */
public enum LeadSource {
    WEBSITE,
    REFERRAL,
    WALK_IN,
    PHONE,
    EMAIL,
    SOCIAL_MEDIA,
    ADVERT,
    EVENT,
    PARTNER,
    OTHER
}
