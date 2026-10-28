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

/** The privacy-notice link as it may be shown: an http(s) address only. Anything else (javascript:, data:, a bare word) is not linked. */
export function safePrivacyUrl(url: string | null | undefined): string | null {
  const u = url?.trim()
  if (!u || u.length > 500 || /\s/.test(u)) return null
  try {
    const parsed = new URL(u)
    return (parsed.protocol === "https:" || parsed.protocol === "http:") && !!parsed.hostname && !parsed.username ? u : null
  } catch { return null }
}

/** For the settings form; null when empty or usable. Mirrors RecruiterConsentRules.privacyUrlProblem. */
export function privacyUrlProblem(raw: string): string | null {
  const u = raw.trim()
  if (!u) return null
  if (u.length > 500) return "The privacy notice link is too long (500 characters at most)."
  if (!/^https?:\/\//i.test(u)) return "The privacy notice link must start with https:// or http://."
  return safePrivacyUrl(u) ? null : "That does not look like a web address."
}
