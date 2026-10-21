// Campaigns and journeys: a named outreach to a list of people, with timed steps. Mirrors the server's checks so mistakes show up before saving.
import { ACTIONS, canPickUser, textLabel, type Action } from "./workflows.logic"

export type CampaignStatus = "DRAFT" | "ACTIVE" | "PAUSED" | "COMPLETED"
export type MemberStatus = "ADDED" | "CONTACTED" | "RESPONDED" | "CONVERTED"
export const STATUS_LABEL: Record<CampaignStatus, string> = { DRAFT: "Draft", ACTIVE: "Running", PAUSED: "Paused", COMPLETED: "Finished" }
export const MEMBER_LABEL: Record<MemberStatus, string> = { ADDED: "Added", CONTACTED: "Contacted", RESPONDED: "Responded", CONVERTED: "Converted" }
export const MEMBER_STATUSES: MemberStatus[] = ["ADDED", "CONTACTED", "RESPONDED", "CONVERTED"]
export const MAX_STEPS = 10, MAX_CAMPAIGNS = 50, MAX_ADD_AT_ONCE = 500

export interface Campaign {
  id: string; name: string; goal: string | null; status: CampaignStatus; startsOn: string | null; endsOn: string | null; steps: number
  total: number; contacted: number; responded: number; converted: number; responseRate: number | null; conversionRate: number | null
}
export interface JourneyStep { position: number; dayOffset: number; action: Action; text: string | null; user: string | null; description: string }
export interface Person { id: string; customerId: string; name: string; status: MemberStatus; addedAt: string; statusAt: string; stepsDone: number; exited: boolean }
export interface JourneyRun { id: string; customerId: string; customerName: string; step: number; outcome: "DONE" | "FAILED"; detail: string | null; ranAt: string }

/** What the buttons on a campaign offer, by status. The server enforces the same moves. */
export const NEXT_STATUS: Record<CampaignStatus, { to: CampaignStatus; label: string }[]> = {
  DRAFT: [{ to: "ACTIVE", label: "Start" }],
  ACTIVE: [{ to: "PAUSED", label: "Pause" }, { to: "COMPLETED", label: "Finish" }],
  PAUSED: [{ to: "ACTIVE", label: "Resume" }, { to: "COMPLETED", label: "Finish" }],
  COMPLETED: [],
}
/** Steps can only change while nothing is running them. */
export const stepsLocked = (s: CampaignStatus) => s === "ACTIVE" || s === "COMPLETED"
export const peopleLocked = (s: CampaignStatus) => s === "COMPLETED"

export const rate = (n: number | null | undefined) => (n == null ? "—" : `${n}%`)

export interface CampaignForm { name: string; goal: string; startsOn: string; endsOn: string }
export const EMPTY_CAMPAIGN: CampaignForm = { name: "", goal: "", startsOn: "", endsOn: "" }
export const toCampaignForm = (c: Campaign): CampaignForm => ({ name: c.name, goal: c.goal ?? "", startsOn: c.startsOn ?? "", endsOn: c.endsOn ?? "" })
export function campaignProblem(f: CampaignForm): string | null {
  if (!f.name.trim()) return "Name the campaign."
  if (f.name.trim().length > 120) return "The name can be at most 120 characters."
  if (f.goal.trim().length > 500) return "The goal can be at most 500 characters."
  if (f.startsOn && f.endsOn && f.endsOn < f.startsOn) return "The end date cannot be before the start date."
  return null
}
export const campaignBody = (f: CampaignForm) => ({ name: f.name.trim(), goal: f.goal.trim() || null, startsOn: f.startsOn || null, endsOn: f.endsOn || null })

export interface StepForm { day: string; action: Action | ""; text: string; user: string }
export const EMPTY_STEP: StepForm = { day: "0", action: "", text: "", user: "" }
export const toStepForm = (s: JourneyStep): StepForm => ({ day: String(s.dayOffset), action: s.action, text: s.text ?? "", user: s.user ?? "" })
/** The first thing wrong with the list of steps, or null. Days run forward: a step cannot come before the one above it. */
export function stepsProblem(steps: StepForm[]): string | null {
  if (steps.length > MAX_STEPS) return `A journey can have at most ${MAX_STEPS} steps.`
  let previous = 0
  for (let i = 0; i < steps.length; i++) {
    const s = steps[i], where = `Step ${i + 1}: `, n = Number(s.day.trim())
    if (!s.day.trim() || !Number.isInteger(n) || n < 0 || n > 365) return `${where}the day must be a whole number from 0 to 365.`
    if (n < previous) return `${where}it cannot come before the step above it.`
    previous = n
    if (!s.action) return `${where}choose what it does.`
    const t = s.text.trim()
    if (s.action === "CREATE_TASK") { if (!t) return `${where}say what the task is.`; if (t.length > 500) return `${where}the task can be at most 500 characters.` }
    if (s.action === "ADD_TAG") { if (!t) return `${where}name the tag.`; if (t.length > 50) return `${where}the tag can be at most 50 characters.` }
    if (s.action === "ASSIGN_OWNER" && !s.user) return `${where}choose who becomes the owner.`
    if (s.action === "NOTIFY_OWNER") { if (!t) return `${where}write the message.`; if (t.length > 500) return `${where}the message can be at most 500 characters.` }
  }
  return null
}
export const stepsBody = (steps: StepForm[]) => steps.map(s => ({
  dayOffset: Number(s.day.trim()), action: s.action, text: s.action && s.action !== "ASSIGN_OWNER" ? s.text.trim() : null, user: s.action && canPickUser(s.action) && s.user ? s.user : null,
}))
export { ACTIONS, canPickUser, textLabel }

export interface AddForm { segmentId: string; customerIds: string[] }
export const addProblem = (segmentId: string, picked: string[]): string | null =>
  !segmentId && picked.length === 0 ? "Choose a segment or pick some people." : picked.length > MAX_ADD_AT_ONCE ? `Add at most ${MAX_ADD_AT_ONCE} people at a time.` : null
export const addBody = (segmentId: string, picked: string[]) => ({ segmentId: segmentId || null, customerIds: picked })
/** The line shown after people are added. */
export function addedText(r: { added: number; alreadyIn: number; skipped: number }): string {
  const parts = [`${r.added} ${r.added === 1 ? "person" : "people"} added`]
  if (r.alreadyIn) parts.push(`${r.alreadyIn} already in`)
  if (r.skipped) parts.push(`${r.skipped} could not be found`)
  return parts.join(", ") + "."
}
/** Where someone is on the journey. */
export const progressText = (p: Person, steps: number) => (steps === 0 ? "No steps" : p.exited && p.stepsDone < steps ? "Left the journey" : `${Math.min(p.stepsDone, steps)} of ${steps} steps`)
export const runSummary = (rs: JourneyRun[]) => ({ done: rs.filter(r => r.outcome === "DONE").length, failed: rs.filter(r => r.outcome === "FAILED").length })
