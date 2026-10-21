// Rule conditions shared by lead scoring, lead routing and workflow automation. Mirrors the server's checks so mistakes show up before saving.
import { LEAD_SOURCES } from "./pipeline.logic"

export type CondField = "SOURCE" | "STAGE" | "DEAL_VALUE" | "HAS_EMAIL" | "HAS_PHONE" | "HAS_OWNER" | "TAG"
export type CondOp = "EQ" | "GTE" | "LTE"
export interface Condition { field: CondField | ""; op: CondOp | ""; value: string }
export const NO_CONDITION: Condition = { field: "", op: "", value: "" }

export const FIELDS: { id: CondField; label: string }[] = [
  { id: "SOURCE", label: "Lead source" }, { id: "STAGE", label: "Pipeline stage" }, { id: "DEAL_VALUE", label: "Deal value" },
  { id: "HAS_EMAIL", label: "Has an email" }, { id: "HAS_PHONE", label: "Has a phone number" }, { id: "HAS_OWNER", label: "Has an owner" }, { id: "TAG", label: "Tag" },
]
export const fieldLabel = (f: CondField) => FIELDS.find(x => x.id === f)?.label ?? f
export const STAGE_CHOICES = [{ id: "NEW", label: "New" }, { id: "CONTACTED", label: "Contacted" }, { id: "QUALIFIED", label: "Qualified" }, { id: "WON", label: "Won" }, { id: "LOST", label: "Lost" }]
export const YES_NO = [{ id: "true", label: "Yes" }, { id: "false", label: "No" }]
/** The comparisons that make sense for a field. */
export const opsFor = (f: CondField): { id: CondOp; label: string }[] =>
  f === "DEAL_VALUE" ? [{ id: "GTE", label: "is at least" }, { id: "LTE", label: "is at most" }] : [{ id: "EQ", label: "is" }]
/** The fixed choices for a field, or null when the value is typed. */
export function choicesFor(f: CondField): { id: string; label: string }[] | null {
  if (f === "SOURCE") return LEAD_SOURCES.map(s => ({ id: s.id, label: s.label }))
  if (f === "STAGE") return STAGE_CHOICES
  if (f === "HAS_EMAIL" || f === "HAS_PHONE" || f === "HAS_OWNER") return YES_NO
  return null
}
/** A condition with its field chosen and a sensible first comparison. Changing the field clears the value. */
export const withField = (f: CondField | ""): Condition => (f ? { field: f, op: opsFor(f)[0].id, value: "" } : NO_CONDITION)

/** Why the condition is not complete, or null. `optional` means an empty condition is allowed (it then matches every lead). */
export function conditionProblem(c: Condition, optional = false): string | null {
  if (!c.field) return optional ? null : "Choose what to check."
  if (!c.value.trim()) return "Give the value to compare with."
  if (c.value.trim().length > 100) return "The value can be at most 100 characters."
  if (c.field === "DEAL_VALUE") {
    const n = Number(c.value.trim().replace(/\s/g, ""))
    if (!Number.isFinite(n)) return "The deal value must be a number."
    if (n < 0) return "The deal value cannot be negative."
  }
  return null
}
/** What the API takes: all three empty for "every lead". */
export const conditionBody = (c: Condition) => (c.field ? { field: c.field, op: c.op || opsFor(c.field)[0].id, value: c.field === "DEAL_VALUE" ? c.value.trim().replace(/\s/g, "") : c.value.trim() } : { field: null, op: null, value: null })

// ── Scoring ─────────────────────────────────────────────────────────────────
export interface ScoringRule { id: string; name: string; field: CondField; op: CondOp; value: string; points: number; active: boolean; description: string }
export type Band = "HOT" | "WARM" | "COLD"
export interface LeadScore { customerId: string; score: number; band: Band; reasons: string[] }
export const BAND_LABEL: Record<Band, string> = { HOT: "Hot", WARM: "Warm", COLD: "Cold" }
export const BAND_TONE: Record<Band, string> = { HOT: "var(--hf-danger-text)", WARM: "var(--hf-warning-text)", COLD: "var(--hf-text-muted)" }
export function pointsProblem(text: string): string | null {
  const t = text.trim()
  if (!t) return "Enter the points."
  const n = Number(t)
  if (!Number.isInteger(n)) return "Points must be a whole number."
  if (n === 0 || n < -100 || n > 100) return "Points must be between -100 and 100, and not zero."
  return null
}
export interface ScoringForm { name: string; condition: Condition; points: string; active: boolean }
export const EMPTY_SCORING: ScoringForm = { name: "", condition: NO_CONDITION, points: "10", active: true }
export const toScoringForm = (r: ScoringRule): ScoringForm => ({ name: r.name, condition: { field: r.field, op: r.op, value: r.value }, points: String(r.points), active: r.active })
export function scoringProblem(f: ScoringForm): string | null {
  if (!f.name.trim()) return "Name the scoring rule."
  if (f.name.trim().length > 120) return "The name can be at most 120 characters."
  return conditionProblem(f.condition) ?? pointsProblem(f.points)
}
export const scoringBody = (f: ScoringForm) => ({ name: f.name.trim(), ...conditionBody(f.condition), points: Number(f.points.trim()), active: f.active })
export const scoreMap = (scores: LeadScore[]): Map<string, LeadScore> => new Map(scores.filter(s => s && s.customerId).map(s => [s.customerId, s]))

// ── Routing ─────────────────────────────────────────────────────────────────
export interface RoutingRule { id: string; name: string; position: number; field: CondField | null; op: CondOp | null; value: string | null; description: string; assigneeIds: string[]; active: boolean }
export interface RoutingForm { name: string; condition: Condition; assigneeIds: string[]; active: boolean }
export const EMPTY_ROUTING: RoutingForm = { name: "", condition: NO_CONDITION, assigneeIds: [], active: true }
export const toRoutingForm = (r: RoutingRule): RoutingForm => ({ name: r.name, condition: r.field ? { field: r.field, op: r.op ?? "EQ", value: r.value ?? "" } : NO_CONDITION, assigneeIds: r.assigneeIds, active: r.active })
export function routingProblem(f: RoutingForm): string | null {
  if (!f.name.trim()) return "Name the routing rule."
  if (f.name.trim().length > 120) return "The name can be at most 120 characters."
  const c = conditionProblem(f.condition, true)
  if (c) return c
  if (f.assigneeIds.length === 0) return "Choose at least one person to receive the leads."
  if (f.assigneeIds.length > 20) return "A rule can have at most 20 people."
  return null
}
export const routingBody = (f: RoutingForm) => ({ name: f.name.trim(), ...conditionBody(f.condition), assigneeIds: f.assigneeIds, active: f.active })
/** The ids in their new order after moving one rule up (-1) or down (+1); unchanged at the ends. */
export function movedOrder(ids: string[], id: string, by: -1 | 1): string[] {
  const i = ids.indexOf(id), j = i + by
  if (i < 0 || j < 0 || j >= ids.length) return ids
  const out = [...ids]; [out[i], out[j]] = [out[j], out[i]]
  return out
}
export const routeResultText = (r: { routed: number; left: number }) =>
  r.routed === 0 && r.left === 0 ? "Every open lead already has an owner." : `${r.routed} ${r.routed === 1 ? "lead was" : "leads were"} given an owner.${r.left ? ` ${r.left} still ${r.left === 1 ? "has" : "have"} none: no rule fits ${r.left === 1 ? "it" : "them"}.` : ""}`
