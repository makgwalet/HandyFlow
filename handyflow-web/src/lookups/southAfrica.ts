// src/lookups/southAfrica.ts
//
// Pick-lists for the data that is typed over and over and that other screens match on (a requirement's "CSD" must equal a registration's "CSD").
// They are suggestions, not a cage: anything not on a list can still be typed, and is marked as custom.
// Pure data and two helpers. No React, no network.
export interface LookupOption { value: string; hint?: string }

const opts = (values: string[]): LookupOption[] => values.map(value => ({ value }))

export const REGISTRATION_AUTHORITIES = ["CIPC", "SARS", "UIF", "PSIRA", "CSD", "CIDB", "NHBRC", "COIDA", "OTHER"]

/** CIDB grades 1 to 9 for each class of work, written the way tenders write them: "6CE". */
export const CIDB_CLASSES: { code: string; name: string }[] = [
  { code: "GB", name: "General building" }, { code: "CE", name: "Civil engineering" }, { code: "ME", name: "Mechanical engineering" },
  { code: "EB", name: "Electrical engineering (buildings)" }, { code: "EP", name: "Electrical engineering (infrastructure)" },
]
export const CIDB_GRADINGS: LookupOption[] = CIDB_CLASSES.flatMap(c => Array.from({ length: 9 }, (_, i) => ({ value: `Grade ${i + 1}${c.code}`, hint: c.name })))
export const CLASS_OF_WORK: LookupOption[] = CIDB_CLASSES.flatMap(c => Array.from({ length: 9 }, (_, i) => ({ value: `cidb Grade ${i + 1}${c.code}`, hint: c.name })))

const REGISTRATION_TYPES: Record<string, LookupOption[]> = {
  CIPC: opts(["Business Registration", "Annual Return", "Beneficial Ownership"]),
  SARS: opts(["Income Tax", "VAT", "PAYE", "Tax Compliance Status", "Customs"]),
  UIF: opts(["Employer Registration", "Letter of Good Standing"]),
  PSIRA: opts(["Security Business Registration", "Grade A", "Grade B", "Grade C", "Grade D", "Grade E"]),
  CSD: opts(["Supplier Registration"]),
  CIDB: CIDB_GRADINGS,
  NHBRC: opts(["Home Builder Registration", "Enrolment"]),
  COIDA: opts(["Registration", "Letter of Good Standing"]),
  OTHER: [],
}
export function registrationTypesFor(authority: string): LookupOption[] { return REGISTRATION_TYPES[authority.trim().toUpperCase()] ?? [] }

export const DOCUMENT_TYPES: LookupOption[] = opts([
  "Tax Compliance Status (TCS) PIN Letter", "CSD Summary Report", "CIPC Registration Certificate", "CIPC Annual Return Confirmation",
  "B-BBEE Certificate", "B-BBEE Sworn Affidavit", "CIDB Registration Certificate", "COIDA Letter of Good Standing", "UIF Letter of Good Standing",
  "VAT Registration Certificate", "PSIRA Certificate", "NHBRC Certificate", "Public Liability Insurance Certificate", "Professional Indemnity Insurance Certificate",
  "Bank Confirmation Letter", "Company Resolution / Letter of Authority", "Director ID Copy", "Proof of Business Address", "Municipal Rates and Services Statement",
  "Financial Statements", "Health and Safety Plan", "Method Statement", "Reference Letter", "ISO Certificate", "Certificate of Competency", "CV",
])

export const DEADLINE_TYPES: LookupOption[] = [
  { value: "ANNUAL_RETURN", hint: "CIPC annual return" }, { value: "VAT_RETURN", hint: "VAT201 return" }, { value: "EMP201", hint: "Monthly PAYE / UIF / SDL" },
  { value: "EMP501", hint: "PAYE reconciliation" }, { value: "PROVISIONAL_TAX", hint: "Provisional tax payment" }, { value: "INCOME_TAX_RETURN", hint: "Company income tax" },
  { value: "COIDA_RETURN_OF_EARNINGS", hint: "Compensation Fund return" }, { value: "BBBEE_RENEWAL", hint: "B-BBEE certificate renewal" },
  { value: "CIDB_RENEWAL", hint: "cidb registration renewal" }, { value: "CSD_UPDATE", hint: "CSD profile update" }, { value: "TCS_RENEWAL", hint: "Tax compliance status renewal" },
  { value: "INSURANCE_RENEWAL", hint: "Insurance renewal" }, { value: "LICENCE_RENEWAL", hint: "Licence renewal" },
]

export const TENDER_AUTHORITIES: LookupOption[] = opts([
  "SANRAL", "Eskom", "Transnet", "PRASA", "Rand Water", "Department of Public Works and Infrastructure", "Department of Water and Sanitation",
  "Department of Transport", "Department of Health", "Department of Basic Education", "Department of Human Settlements",
  "City of Johannesburg", "City of Cape Town", "City of Tshwane", "eThekwini Municipality", "Ekurhuleni Metropolitan Municipality",
  "Nelson Mandela Bay Metropolitan Municipality", "Buffalo City Metropolitan Municipality", "Mangaung Metropolitan Municipality",
])

export const INDUSTRIES = opts([
  "Construction", "Civil engineering", "Electrical", "Mechanical", "Security services", "Cleaning and hygiene", "Facilities management", "Catering",
  "ICT", "Professional services", "Transport and logistics", "Agriculture", "Health services", "Training and education", "Supply of goods", "Other",
])

export const APPLIES_TO = opts(["All tenders", "Government tender", "Municipal tender", "State-owned entity tender", "Private tender"])

export const UNITS = opts(["m", "m²", "m³", "km", "no", "sum", "item", "set", "hr", "day", "week", "month", "kg", "t", "l"])

export const PERSONNEL_ROLES = opts([
  "Project Manager", "Contracts Manager", "Construction Manager", "Site Agent", "Site Foreman", "Quantity Surveyor", "Civil Engineer", "Electrical Engineer",
  "Health and Safety Officer", "Quality Controller", "Environmental Officer", "Plant Operator", "Finance Manager",
])

/** The list's own spelling of what was typed (ignoring case and extra spaces), or null when it is not on the list. */
export function canonical(value: string, options: LookupOption[]): string | null {
  const key = value.trim().replace(/\s+/g, " ").toLowerCase()
  if (!key) return null
  return options.find(o => o.value.toLowerCase() === key)?.value ?? null
}

/** What to store when the field is left: the list's spelling when it matches, otherwise the text tidied of stray spaces. */
export function settle(value: string, options: LookupOption[]): string {
  return canonical(value, options) ?? value.trim().replace(/\s+/g, " ")
}

export function isCustom(value: string, options: LookupOption[]): boolean {
  return value.trim() !== "" && options.length > 0 && canonical(value, options) === null
}
