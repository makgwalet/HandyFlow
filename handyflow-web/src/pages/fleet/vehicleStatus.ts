// How a vehicle's status looks, and the small formatters the Vehicles screen uses.
import { Car, AlertTriangle, CheckCircle, Wrench, Clock } from "lucide-react"

export const STATUS_CFG: Record<string, { color: string; bg: string; border: string; label: string; icon: React.ElementType }> = {
  AVAILABLE:   { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", border: "var(--hf-success-border)",  label: "Available",   icon: CheckCircle  },
  ON_TRIP:     { color: "var(--hf-info-text)", bg: "var(--hf-info-soft)", border: "var(--hf-info-border)",  label: "On Trip",     icon: Car          },
  MAINTENANCE: { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", border: "var(--hf-warning-border)",  label: "Maintenance", icon: Wrench       },
  BREAKDOWN:   { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", border: "var(--hf-danger-border)",  label: "Breakdown",   icon: AlertTriangle },
  RETIRED:     { color: "var(--hf-text-faint)", bg: "var(--hf-surface-muted)", border: "var(--hf-border)",  label: "Retired",     icon: Clock        },
}

export const fmtOdo  = (km: number) => `${Number(km).toLocaleString("en-ZA")} km`
export const fmtR    = (n: number | null | undefined) => n != null ? `R ${Number(n).toLocaleString("en-ZA")}` : "—"
export const fmtDate = (d: string | null) => d ? new Date(d).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" }) : "—"
export const daysUntil = (d: string | null) => d ? Math.ceil((new Date(d).getTime() - Date.now()) / 86400000) : 999
