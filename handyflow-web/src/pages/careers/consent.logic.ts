// src/pages/careers/consent.logic.ts
//
// The consent wording on the apply form. Mirrors RecruiterConsentRules.statementFor on the server.
export const GENERAL_STATEMENT =
  "I agree that this company may keep my application, CV and contact details to consider me for this and similar roles, and that they will be removed after the company's retention period. I can ask to see, correct or delete them at any time."

export function consentStatement(retentionMonths: number | null | undefined): string {
  if (retentionMonths == null || !Number.isFinite(retentionMonths) || retentionMonths < 1) return GENERAL_STATEMENT
  return "I agree that this company may keep my application, CV and contact details to consider me for this and similar roles, and that they will be removed "
    + `${retentionMonths} ${retentionMonths === 1 ? "month" : "months"} after my last activity unless I am hired. I can ask to see, correct or delete them at any time.`
}
