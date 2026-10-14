// Finance F23: helpers for the collection strategies screen.
export const ACTION_LABEL: Record<string, string> = {
  CALL: "Call the debtor", SEND_REMINDER: "Send a reminder", SEND_DEMAND: "Send a formal demand",
  LEGAL_REVIEW: "Review for legal action", REVIEW_WRITE_OFF: "Review for write-off", OTHER: "Other follow-up",
}
export const DECISION_LABEL: Record<string, string> = { RAISE: "Will raise a task", WAIT: "Nothing due", SKIP: "Left alone", NONE: "No strategy" }

export interface StepForm { afterDays: string; action: string; instruction: string }
export interface StrategyForm {
  name: string; description: string; priority: string; minBalance: string; maxBalance: string; minDays: string; maxDays: string; quietDays: string; steps: StepForm[]
}
export const emptyStrategy = (): StrategyForm => ({
  name: "", description: "", priority: "10", minBalance: "", maxBalance: "", minDays: "", maxDays: "", quietDays: "3",
  steps: [{ afterDays: "7", action: "SEND_REMINDER", instruction: "" }],
})
const num = (s: string) => Number(s.replace(",", "."))
const opt = (s: string) => (s.trim() === "" ? null : num(s))

export function strategyProblem(f: StrategyForm): string | null {
  if (!f.name.trim()) return "Give the strategy a name."
  if (!(num(f.priority) >= 1 && num(f.priority) <= 999)) return "Order must be between 1 and 999."
  for (const [label, v] of [["smallest balance", f.minBalance], ["largest balance", f.maxBalance], ["fewest days", f.minDays], ["most days", f.maxDays]] as const)
    if (v.trim() !== "" && !(num(v) >= 0)) return `The ${label} must be a number, zero or more.`
  if (f.minBalance.trim() && f.maxBalance.trim() && num(f.maxBalance) < num(f.minBalance)) return "The largest balance is below the smallest."
  if (f.minDays.trim() && f.maxDays.trim() && num(f.maxDays) < num(f.minDays)) return "The most days is below the fewest."
  if (!(num(f.quietDays) >= 0 && num(f.quietDays) <= 30)) return "Days to wait after contact must be 0 to 30."
  if (f.steps.length === 0) return "Add at least one step."
  const days = new Set<number>()
  for (const s of f.steps) {
    const d = num(s.afterDays)
    if (s.afterDays.trim() === "" || !(d >= 0 && d <= 730)) return "Each step needs a day count from 0 to 730."
    if (days.has(d)) return `Two steps start on day ${d}.`
    days.add(d)
  }
  return null
}

export const strategyRequest = (f: StrategyForm) => ({
  name: f.name.trim(), description: f.description.trim() || null, priority: num(f.priority),
  minBalance: opt(f.minBalance), maxBalance: opt(f.maxBalance), minDays: opt(f.minDays), maxDays: opt(f.maxDays), quietDays: num(f.quietDays),
  steps: [...f.steps].sort((a, b) => num(a.afterDays) - num(b.afterDays)).map(s => ({ afterDays: num(s.afterDays), action: s.action, instruction: s.instruction.trim() || null })),
})

export const formFromStrategy = (s: any): StrategyForm => ({
  name: s.name, description: s.description ?? "", priority: String(s.priority),
  minBalance: s.minBalance == null ? "" : String(s.minBalance), maxBalance: s.maxBalance == null ? "" : String(s.maxBalance),
  minDays: s.minDays == null ? "" : String(s.minDays), maxDays: s.maxDays == null ? "" : String(s.maxDays), quietDays: String(s.quietDays),
  steps: s.steps.map((x: any) => ({ afterDays: String(x.afterDays), action: x.action, instruction: x.instruction ?? "" })),
})

/** A one-line description of who a strategy covers. */
export function segmentText(s: { minBalance: number | null; maxBalance: number | null; minDays: number | null; maxDays: number | null }): string {
  const r = (n: number) => `R ${n.toLocaleString("en-ZA")}`
  const parts: string[] = []
  if (s.minBalance != null && s.maxBalance != null) parts.push(`balance ${r(s.minBalance)} to ${r(s.maxBalance)}`)
  else if (s.minBalance != null) parts.push(`balance from ${r(s.minBalance)}`)
  else if (s.maxBalance != null) parts.push(`balance up to ${r(s.maxBalance)}`)
  if (s.minDays != null && s.maxDays != null) parts.push(`${s.minDays} to ${s.maxDays} days overdue`)
  else if (s.minDays != null) parts.push(`${s.minDays}+ days overdue`)
  else if (s.maxDays != null) parts.push(`up to ${s.maxDays} days overdue`)
  return parts.length ? parts.join(", ") : "every case"
}
