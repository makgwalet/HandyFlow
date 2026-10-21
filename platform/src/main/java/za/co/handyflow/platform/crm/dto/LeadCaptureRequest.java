package za.co.handyflow.platform.crm.dto;

/**
 * What a website form or another system sends. name is required (the contact, or the company when company is empty).
 * source is optional (a lead source such as WEBSITE or REFERRAL; anything else uses the key's default).
 * fields are values for the account's custom fields, by field key; an account that marks a custom field required needs it here.
 * website is a trap for form-filling robots: people leave it empty, so a lead that has it filled in is quietly ignored.
 */
public record LeadCaptureRequest(String name, String company, String email, String phone, String message, String source, java.util.Map<String, String> fields, String website) {}
