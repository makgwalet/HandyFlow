// src/pages/payroll-bureau/validation.ts
//
// Shared field-level validation for both employee and client forms.
// Real gap found via direct feedback: forms only ever checked
// firstName/lastName/grossSalary with one generic error string at the
// bottom — no per-field feedback, no format checks on ID/tax number/
// phone beyond the browser's native type="email".

export type FieldErrors = Record<string, string>

export function required(value: string | undefined | null, label: string): string | null {
  return value && value.trim() ? null : `${label} is required`
}

// SA ID numbers are 13 digits. Format-only check (not a full Luhn/
// checksum validation) — flags an obviously wrong entry (letters,
// wrong length) without being so strict it blocks a real edge case
// this validator doesn't know about.
export function validateSaId(value: string): string | null {
  if (!value) return null // optional field
  if (!/^\d{13}$/.test(value)) return "ID number must be 13 digits"
  return null
}

// SA tax reference numbers are 10 digits.
export function validateTaxNumber(value: string): string | null {
  if (!value) return null
  if (!/^\d{10}$/.test(value)) return "Tax number must be 10 digits"
  return null
}

export function validateEmail(value: string): string | null {
  if (!value) return null
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return "Enter a valid email address"
  return null
}

// Loose on purpose — SA phone numbers appear in this codebase in
// several shapes (082..., +2782..., 011...) and this isn't the place
// to force one convention on existing data.
export function validatePhone(value: string): string | null {
  if (!value) return null
  if (!/^[\d+\s()-]{7,20}$/.test(value)) return "Enter a valid phone number"
  return null
}

export function validatePositiveNumber(value: string, label: string): string | null {
  if (!value) return `${label} is required`
  const n = Number(value)
  if (Number.isNaN(n) || n < 0) return `${label} must be a positive number`
  return null
}

export function validateDayOfMonth(value: string): string | null {
  if (!value) return null
  const n = Number(value)
  if (Number.isNaN(n) || n < 1 || n > 31) return "Enter a day between 1 and 31"
  return null
}
