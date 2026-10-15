// src/pages/earthmoving/shared/constants.ts
import {
  Truck, AlertTriangle, MapPin, Wrench, CheckCircle, Clock,
} from "lucide-react"

export const STATUS_CFG: Record<string, { color: string; bg: string; border: string; label: string; icon: React.ElementType }> = {
  AVAILABLE:   { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", border: "var(--hf-success-border)", label: "Available",   icon: CheckCircle  },
  DEPLOYED:    { color: "var(--hf-info-text)", bg: "var(--hf-info-soft)", border: "var(--hf-info-border)", label: "Deployed",    icon: MapPin       },
  MAINTENANCE: { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", border: "var(--hf-warning-border)", label: "Maintenance", icon: Wrench       },
  BREAKDOWN:   { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", border: "var(--hf-danger-border)", label: "Breakdown",   icon: AlertTriangle },
  HIRED_OUT:   { color: "var(--hf-violet-text)", bg: "var(--hf-violet-soft)", border: "var(--hf-violet-border)", label: "Hired Out",   icon: Truck        },
  RETIRED:     { color: "var(--hf-text-faint)", bg: "var(--hf-surface-muted)", border: "var(--hf-border)", label: "Retired",     icon: Clock        },
}

export const STATUS_DESCRIPTIONS: Record<string, string> = {
  AVAILABLE:   "Machine is in the yard, ready to deploy",
  DEPLOYED:    "Machine is active on a site",
  MAINTENANCE: "Machine is undergoing scheduled maintenance",
  BREAKDOWN:   "Machine is unserviceable due to breakdown or accident",
  HIRED_OUT:   "Machine is hired out to a third party",
  RETIRED:     "Machine has been permanently decommissioned",
}

export const OWN_TYPE_CFG: Record<string, { color: string; bg: string; label: string }> = {
  OWN:       { color: "var(--hf-primary-text)", bg: "var(--hf-info-soft)", label: "Owned"     },
  HIRED_IN:  { color: "var(--hf-violet-text)", bg: "var(--hf-violet-soft)", label: "Hired In"  },
  HIRED_OUT: { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", label: "Hired Out" },
}

export const ASSET_TYPES = [
  "DOZER", "EXCAVATOR", "GRADER", "LOADER", "DUMPER", "CRANE",
  "ROLLER", "SCRAPER", "COMPACTOR", "DRILL", "OTHER",
]

// NOTE: must stay in sync with AssetStatus.java on the backend. If you add
// a status there, add it here too, or filter chips for it will silently
// never appear.
export const STATUSES = ["AVAILABLE", "DEPLOYED", "MAINTENANCE", "BREAKDOWN", "HIRED_OUT", "RETIRED"]

export const EMOJI: Record<string, string> = {
  DOZER: "🚜", EXCAVATOR: "⛏️", GRADER: "🛣️", LOADER: "🏗️",
  DUMPER: "🚛", CRANE: "🏗️", ROLLER: "🛞", SCRAPER: "🚜",
  COMPACTOR: "🛞", DRILL: "⛏️", OTHER: "🚧",
}

export const MAINTENANCE_TYPES = [
  "SERVICE", "REPAIR", "INSPECTION", "TYRE", "BATTERY",
  "ELECTRICAL", "HYDRAULICS", "ENGINE", "TRACKS", "OTHER",
]

export const MAINTENANCE_TYPE_CFG: Record<string, { color: string; bg: string }> = {
  SERVICE:    { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)" },
  REPAIR:     { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)" },
  INSPECTION: { color: "var(--hf-info-text)", bg: "var(--hf-info-soft)" },
  TYRE:       { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)" },
  BATTERY:    { color: "var(--hf-violet-text)", bg: "var(--hf-violet-soft-strong)" },
  ELECTRICAL: { color: "var(--hf-sky-text-strong)", bg: "var(--hf-sky-soft)" },
  HYDRAULICS: { color: "var(--hf-sky-text)", bg: "var(--hf-sky-soft)" },
  ENGINE:     { color: "var(--hf-warning-text-strong)", bg: "var(--hf-warning-soft-strong)" },
  TRACKS:     { color: "var(--hf-text-secondary)", bg: "var(--hf-surface-muted)" },
  OTHER:      { color: "var(--hf-text-muted)", bg: "var(--hf-surface-muted)" },
}

export const INCIDENT_TYPES = ["BREAKDOWN", "ACCIDENT", "THEFT", "FIRE", "ROLLOVER", "NEAR_MISS", "FUEL_SPILL", "OTHER"]

export const SEVERITY_CFG: Record<string, { color: string; bg: string; border: string }> = {
  CRITICAL: { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", border: "var(--hf-danger-border)" },
  HIGH:     { color: "var(--hf-orange-text)", bg: "var(--hf-orange-soft)", border: "var(--hf-orange-border)" },
  MEDIUM:   { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", border: "var(--hf-warning-border)" },
  LOW:      { color: "var(--hf-text-muted)", bg: "var(--hf-surface-muted)", border: "var(--hf-border)" },
}

// Mirrors AssetStatus.java's transition table (keep in step). DEPLOYED is never set from the status dialog: use Deploy,
// which records the site and client.
export const ALERT_TYPE_LABEL: Record<string, string> = {
  SERVICE_DUE: "Service due", BREAKDOWN: "Breakdown or accident", WORK_ORDER_OVERDUE: "Overdue work order",
}

export const STATUS_TRANSITIONS: Record<string, string[]> = {
  AVAILABLE:   ["MAINTENANCE", "HIRED_OUT", "RETIRED"],
  DEPLOYED:    ["AVAILABLE", "BREAKDOWN", "MAINTENANCE"],
  MAINTENANCE: ["AVAILABLE", "BREAKDOWN", "RETIRED"],
  BREAKDOWN:   ["AVAILABLE", "MAINTENANCE", "RETIRED"],
  HIRED_OUT:   ["AVAILABLE", "BREAKDOWN"],
  RETIRED:     [],
}

/** The permission needed to move from one status to another (mirrors EarthmovingRules.authorityForStatusChange). */
export function authorityForStatusChange(from: string, to: string): string {
  switch (to) {
    case "BREAKDOWN":   return "EARTHMOVING_WRITE"
    case "MAINTENANCE": return "EARTHMOVING_MAINTAIN"
    case "HIRED_OUT":   return "EARTHMOVING_DEPLOY"
    case "RETIRED":     return "EARTHMOVING_ADMIN"
    default:            return from === "MAINTENANCE" || from === "BREAKDOWN" ? "EARTHMOVING_RELEASE" : "EARTHMOVING_DEPLOY"
  }
}

export const isRelease = (from: string, to: string) =>
  to === "AVAILABLE" && (from === "MAINTENANCE" || from === "BREAKDOWN")

/** Returning a deployed machine to the yard needs no written reason; every other change does. */
export const needsReason = (from: string, to: string) => !(from === "DEPLOYED" && to === "AVAILABLE")

export const EARTHMOVING_PERMS = {
  READ: "EARTHMOVING_READ", WRITE: "EARTHMOVING_WRITE", DEPLOY: "EARTHMOVING_DEPLOY",
  MAINTAIN: "EARTHMOVING_MAINTAIN", RELEASE: "EARTHMOVING_RELEASE", ADMIN: "EARTHMOVING_ADMIN",
} as const

export const INSPECTION_TYPE_LABEL: Record<string, string> = { PRE_USE: "Pre-use", HANDOVER: "Handover to customer", RETURN: "Return from hire", MAINTENANCE: "Maintenance", SAFETY: "Safety" }

export const CONTRACT_STATUS_LABEL: Record<string, string> = {
  DRAFT: "Draft", UNDER_REVIEW: "Under review", SENT: "Sent, not signed", SIGNED: "Signed",
  EXPIRED: "Expired", TERMINATED: "Terminated", ARCHIVED: "Archived",
}

/** How a hire charge is counted, for choosing a basis and for labelling a rate. */
export const HIRE_BASIS_LABEL: Record<string, string> = {
  DAILY: "Per day", WEEKLY: "Per week", MONTHLY: "Per month", HOURLY: "Per hour", KILOMETRE: "Per kilometre",
}
export const HIRE_RATE_SOURCE_LABEL: Record<string, string> = {
  CONTRACT: "rate from the contract", ASSET: "machine's own rate", AGREED: "rate agreed for this charge",
}

export const ANSWER_TYPE_LABEL: Record<string, string> = { PASS_FAIL: "Pass or fail", NUMERIC: "A number", TEXT: "A note", PHOTO: "A photo" }
