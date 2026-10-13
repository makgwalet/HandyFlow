// The public check that a document was issued by the practice (patch 0194; CLINIC-DEC-025). It shows authenticity only.
export interface VerifyAnswer { valid: boolean; voided: boolean; documentType?: string | null; documentNumber?: string | null; issuedOn?: string | null; issuedBy?: string | null }

/** What was typed or pasted, tidied for display: upper case, groups of four. Anything else is left for the server to refuse. */
export function tidyCode(raw: string): string {
  const s = raw.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 16)
  return s.replace(/(.{4})(?=.)/g, "$1-")
}

export const looksComplete = (raw: string): boolean => raw.toUpperCase().replace(/[^A-Z0-9]/g, "").length === 16

export type Outcome = "GENUINE" | "WITHDRAWN" | "UNKNOWN"

export function outcome(a: VerifyAnswer | null | undefined): Outcome {
  if (!a || !a.valid) return "UNKNOWN"
  return a.voided ? "WITHDRAWN" : "GENUINE"
}

export function headline(o: Outcome): string {
  if (o === "GENUINE") return "This document was issued by the practice."
  if (o === "WITHDRAWN") return "This document was issued by the practice and has since been withdrawn. Do not rely on it."
  return "No document matches that code. Check the code on the document and try again."
}

export const NOT_A_SIGNATURE = "This check shows the practice issued a document with this code. It does not show the document in your hand is unchanged: compare its number and date with the details below."
