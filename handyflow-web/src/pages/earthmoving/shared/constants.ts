// src/pages/earthmoving/shared/constants.ts
import {
  Truck, AlertTriangle, MapPin, Wrench, CheckCircle, Clock,
} from "lucide-react"

export const STATUS_CFG: Record<string, { color: string; bg: string; border: string; label: string; icon: React.ElementType }> = {
  AVAILABLE:   { color: "#166534", bg: "#DCFCE7", border: "#86EFAC", label: "Available",   icon: CheckCircle  },
  DEPLOYED:    { color: "#1D4ED8", bg: "#EFF6FF", border: "#BFDBFE", label: "Deployed",    icon: MapPin       },
  MAINTENANCE: { color: "#D97706", bg: "#FFFBEB", border: "#FDE68A", label: "Maintenance", icon: Wrench       },
  BREAKDOWN:   { color: "#DC2626", bg: "#FEF2F2", border: "#FECACA", label: "Breakdown",   icon: AlertTriangle },
  HIRED_OUT:   { color: "#7C3AED", bg: "#F5F3FF", border: "#DDD6FE", label: "Hired Out",   icon: Truck        },
  RETIRED:     { color: "#94A3B8", bg: "#F8FAFC", border: "#E2E8F0", label: "Retired",     icon: Clock        },
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
  OWN:       { color: "#1B3A6B", bg: "#EFF6FF", label: "Owned"     },
  HIRED_IN:  { color: "#7C3AED", bg: "#F5F3FF", label: "Hired In"  },
  HIRED_OUT: { color: "#D97706", bg: "#FFFBEB", label: "Hired Out" },
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
  SERVICE:    { color: "#166534", bg: "#DCFCE7" },
  REPAIR:     { color: "#DC2626", bg: "#FEF2F2" },
  INSPECTION: { color: "#1D4ED8", bg: "#EFF6FF" },
  TYRE:       { color: "#D97706", bg: "#FFFBEB" },
  BATTERY:    { color: "#7C3AED", bg: "#F3E8FF" },
  ELECTRICAL: { color: "#0369A1", bg: "#F0F9FF" },
  HYDRAULICS: { color: "#0891B2", bg: "#ECFEFF" },
  ENGINE:     { color: "#B45309", bg: "#FEF3C7" },
  TRACKS:     { color: "#374151", bg: "#F9FAFB" },
  OTHER:      { color: "#64748B", bg: "#F8FAFC" },
}

export const INCIDENT_TYPES = ["BREAKDOWN", "ACCIDENT", "THEFT", "FIRE", "ROLLOVER", "NEAR_MISS", "FUEL_SPILL", "OTHER"]

export const SEVERITY_CFG: Record<string, { color: string; bg: string; border: string }> = {
  CRITICAL: { color: "#DC2626", bg: "#FEF2F2", border: "#FECACA" },
  HIGH:     { color: "#EA580C", bg: "#FFF7ED", border: "#FED7AA" },
  MEDIUM:   { color: "#D97706", bg: "#FFFBEB", border: "#FDE68A" },
  LOW:      { color: "#64748B", bg: "#F8FAFC", border: "#E2E8F0" },
}
