// Workflow automation: "when this happens (and the lead fits), do this". Mirrors the server's checks so mistakes show up before saving.
import { NO_CONDITION, conditionBody, conditionProblem, type CondField, type CondOp, type Condition } from "./rules.logic"

export type Trigger = "LEAD_CREATED" | "STAGE_CHANGED" | "OPPORTUNITY_WON"
export type Action = "CREATE_TASK" | "ADD_TAG" | "ASSIGN_OWNER" | "NOTIFY_OWNER"
export const TRIGGERS: { id: Trigger; label: string }[] = [{ id: "LEAD_CREATED", label: "A lead is added" }, { id: "STAGE_CHANGED", label: "A lead changes stage" }, { id: "OPPORTUNITY_WON", label: "An opportunity is won" }]
export const ACTIONS: { id: Action; label: string }[] = [{ id: "CREATE_TASK", label: "Add a task" }, { id: "ADD_TAG", label: "Add a tag" }, { id: "ASSIGN_OWNER", label: "Change the owner" }, { id: "NOTIFY_OWNER", label: "Tell the owner" }]
export const WORKFLOW_STAGES = [{ id: "NEW", label: "New" }, { id: "CONTACTED", label: "Contacted" }, { id: "QUALIFIED", label: "Qualified" }, { id: "WON", label: "Won" }, { id: "LOST", label: "Lost" }]

export interface Workflow {
  id: string; name: string; trigger: Trigger; triggerStage: string | null; condField: CondField | null; condOp: CondOp | null; condValue: string | null
  action: Action; actionText: string | null; actionDays: number | null; actionUser: string | null; active: boolean; description: string
}
export interface WorkflowRun { id: string; workflowId: string; workflowName: string; customerId: string; customerName: string; outcome: "DONE" | "FAILED"; detail: string | null; ranAt: string }

export interface WorkflowForm { name: string; trigger: Trigger | ""; triggerStage: string; condition: Condition; action: Action | ""; text: string; days: string; user: string; active: boolean }
export const EMPTY_WORKFLOW: WorkflowForm = { name: "", trigger: "", triggerStage: "", condition: NO_CONDITION, action: "", text: "", days: "2", user: "", active: true }
export const toWorkflowForm = (w: Workflow): WorkflowForm => ({
  name: w.name, trigger: w.trigger, triggerStage: w.triggerStage ?? "", condition: w.condField ? { field: w.condField, op: w.condOp ?? "EQ", value: w.condValue ?? "" } : NO_CONDITION,
  action: w.action, text: w.actionText ?? "", days: w.actionDays == null ? "2" : String(w.actionDays), user: w.actionUser ?? "", active: w.active,
})
export const MAX_WORKFLOWS = 30

/** What the form asks for, per action. */
export const textLabel = (a: Action | ""): string | null => (a === "CREATE_TASK" ? "What is the task?" : a === "ADD_TAG" ? "Tag" : a === "NOTIFY_OWNER" ? "Message" : null)
export const needsDays = (a: Action | "") => a === "CREATE_TASK"
export const needsUser = (a: Action | "") => a === "ASSIGN_OWNER"
export const canPickUser = (a: Action | "") => a === "ASSIGN_OWNER" || a === "CREATE_TASK"

export function workflowProblem(f: WorkflowForm): string | null {
  if (!f.name.trim()) return "Name the workflow."
  if (f.name.trim().length > 120) return "The name can be at most 120 characters."
  if (!f.trigger) return "Choose what starts the workflow."
  const c = conditionProblem(f.condition, true)
  if (c) return c
  if (!f.action) return "Choose what the workflow does."
  const t = f.text.trim()
  if (f.action === "CREATE_TASK") {
    if (!t) return "Say what the task is."
    if (t.length > 500) return "The task can be at most 500 characters."
    const d = f.days.trim(), n = Number(d)
    if (!d || !Number.isInteger(n) || n < 0 || n > 365) return "Due in days must be a whole number from 0 to 365."
  }
  if (f.action === "ADD_TAG") { if (!t) return "Name the tag."; if (t.length > 50) return "The tag can be at most 50 characters." }
  if (f.action === "ASSIGN_OWNER" && !f.user) return "Choose who becomes the owner."
  if (f.action === "NOTIFY_OWNER") { if (!t) return "Write the message."; if (t.length > 500) return "The message can be at most 500 characters." }
  return null
}
/** The API takes the condition as condField/condOp/condValue, and only sends what the chosen action uses. */
export function workflowBody(f: WorkflowForm) {
  const c = conditionBody(f.condition), a = f.action
  return {
    name: f.name.trim(), trigger: f.trigger, triggerStage: f.trigger === "STAGE_CHANGED" && f.triggerStage ? f.triggerStage : null,
    condField: c.field, condOp: c.op, condValue: c.value,
    action: a, actionText: a && a !== "ASSIGN_OWNER" ? f.text.trim() : null, actionDays: needsDays(a) ? Number(f.days.trim()) : null, actionUser: canPickUser(a) && f.user ? f.user : null, active: f.active,
  }
}
/** The workflow as a sentence, with the person's real name instead of the server's placeholder. */
export const workflowSentence = (w: Workflow) => w.description
export const runSummary = (rs: WorkflowRun[]) => ({ done: rs.filter(r => r.outcome === "DONE").length, failed: rs.filter(r => r.outcome === "FAILED").length })
