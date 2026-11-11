// src/pages/events/overviewOps.ts — pure helpers for the per-event overview: which quick actions apply, which shortcuts a person gets, and the one-line next step.
import { canCancel, canComplete, canDuplicate, canEdit, canGoLive, canPostpone, canPublish } from "./eventActions"

export interface OverviewPerms { write: boolean; approve: boolean; cancel: boolean; ops: boolean; incident: boolean; budget: boolean; payments: boolean; exportPii: boolean }
export type QuickAction = { id: "publish" | "go-live" | "complete"; label: string; kind: "transition" } | { id: "edit" | "postpone" | "copy" | "cancel"; label: string; kind: "dialog" }

/** What can be done to this event right now by this person. The server stays the authority and explains any refusal. */
export function quickActions(status: string, p: Pick<OverviewPerms, "write" | "approve" | "cancel">): QuickAction[] {
  const out: QuickAction[] = []
  if (p.approve && canPublish(status)) out.push({ id: "publish", label: "Publish", kind: "transition" })
  if (p.approve && canGoLive(status)) out.push({ id: "go-live", label: "Go live", kind: "transition" })
  if (p.approve && canComplete(status)) out.push({ id: "complete", label: "Complete event", kind: "transition" })
  if (p.write && canEdit(status)) out.push({ id: "edit", label: "Edit details", kind: "dialog" })
  if (p.write && canPostpone(status)) out.push({ id: "postpone", label: "Postpone", kind: "dialog" })
  if (p.write && canDuplicate(status)) out.push({ id: "copy", label: "Duplicate", kind: "dialog" })
  if (p.cancel && canCancel(status)) out.push({ id: "cancel", label: "Cancel event", kind: "dialog" })
  return out
}

export interface Shortcut { id: string; label: string }
/** Screens to jump to. Everything here is readable with plain event read access except the ones gated like the server gates them. */
export function shortcuts(status: string, p: OverviewPerms): Shortcut[] {
  const live = status === "LIVE"
  const list: (Shortcut | false)[] = [
    { id: "guests", label: live ? "Check in guests" : "Guests" },
    p.write && { id: "add-guests", label: "Add guests" },
    { id: "tickets", label: "Ticket types" },
    p.payments && { id: "payments", label: "Payments" },
    { id: "programme", label: "Run sheet" },
    { id: "checklist", label: "Checklist" },
    p.ops && { id: "staff", label: "Roster" },
    (p.ops || p.incident) && { id: "incidents", label: "Incidents" },
    p.budget && { id: "budget", label: "Budget & costs" },
    { id: "vendors", label: "Vendors" },
    (p.budget || p.exportPii) && { id: "event-reports", label: "Reports & close-out" },
  ]
  return list.filter((x): x is Shortcut => !!x)
}

export interface ReadinessCheck { code: string; label: string; passed: boolean; detail: string | null }
export const failedReadiness = (checks: ReadinessCheck[] | undefined) => (checks ?? []).filter(c => !c.passed)

/** One sentence on what to do next, so the page answers "where are we" without reading every tile. */
export function nextStep(status: string, failed: number): string {
  switch (status) {
    case "DRAFT": return failed > 0 ? `${failed} setup item${failed === 1 ? "" : "s"} to finish before this can be published.` : "Setup looks complete. Publish to open registration."
    case "PUBLISHED": return failed > 0 ? `Registration is open. ${failed} item${failed === 1 ? "" : "s"} still to sort before going live.` : "Registration is open and everything is ready for the day."
    case "SOLD_OUT": return "Sold out. Manage the waitlist or add places."
    case "LIVE": return "The event is live. Check guests in and watch incidents."
    case "COMPLETED": return "The event is over. Open Reports & close-out to wrap up."
    case "CANCELLED": return "This event was cancelled."
    default: return ""
  }
}
export const capacityLine = (registered: number, capacity: number | null | undefined, seatsRemaining: number | null | undefined) =>
  capacity ? `${registered} of ${capacity} registered${seatsRemaining != null ? ` · ${seatsRemaining} left` : ""}` : `${registered} registered · no capacity limit`
