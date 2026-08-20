// src/pages/payroll-bureau/validation.ts
export type FieldErrors = Record<string, string>

export function required(value: string | undefined | null, label: string): string | null {
  return value && value.trim() ? null : `${label} is required`
}

export function validateSaId(value: string): string | null {
  if (!value) return null
  if (!/^\d{13}$/.test(value)) return "ID number must be 13 digits"
  return null
}

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
