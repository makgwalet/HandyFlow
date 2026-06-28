package za.co.handyflow.platform.crm.domain.model;

/**
 * CustomerType — distinguishes prospects from converted clients.
 *
 * WHY does this matter for a CRM?
 * Every real CRM (HubSpot, Pipedrive, Zoho) has this concept.
 * A LEAD is someone you're pursuing but who hasn't transacted yet.
 * A CUSTOMER has booked/invoiced at least once.
 *
 * Having this distinction allows:
 * - Separate list views ("Leads" tab vs "Customers" tab)
 * - Conversion rate reporting ("we converted 12 of 30 leads this month")
 * - Different email flows (onboarding vs retention)
 */
public enum CustomerType {
    /** Prospect — not yet converted to a paying customer. */
    LEAD,
    /** Active or past paying client. */
    CUSTOMER
}
