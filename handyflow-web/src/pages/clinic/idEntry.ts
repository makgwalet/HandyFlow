// What may be typed in the ID box of the registration form. A South African ID is exactly 13 digits; a passport or other
// document is letters and digits (people also have permits and refugee numbers), so it must not be stripped to digits.
import { saIdParts } from "./patientFile.shared"

export type IdType = "SA_ID" | "PASSPORT" | "OTHER"
export const ID_TYPE_OPTIONS: [IdType, string][] = [["SA_ID", "South African ID"], ["PASSPORT", "Passport"], ["OTHER", "Other document"]]
export const ID_LABEL: Record<IdType, string> = { SA_ID: "SA ID number", PASSPORT: "Passport number", OTHER: "Document number" }
export const ID_PLACEHOLDER: Record<IdType, string> = { SA_ID: "8501015026083", PASSPORT: "A01234567", OTHER: "Document number" }

/** The text to keep as someone types: digits only (13 at most) for an SA ID, upper-case letters and digits (20 at most) otherwise. */
export function cleanIdInput(type: IdType, raw: string): string {
  return type === "SA_ID" ? raw.replace(/\D/g, "").slice(0, 13) : raw.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 20)
}

/** A reason the number cannot be kept, or null. Blank is fine: the ID is optional at registration. */
export function idProblem(type: IdType, value: string): string | null {
  if (!value) return null
  if (type !== "SA_ID") return value.length < 5 ? "A passport or document number is at least 5 characters" : null
  if (value.length !== 13) return "An SA ID number is 13 digits"
  return saIdParts(value) ? null : "This is not a valid ID number (check the date part)"
}

/** Only an SA ID tells us the birth date and sex. */
export const autofillsFromId = (type: IdType) => type === "SA_ID"
