// Wording and checks for moderation sampling. This is the provider's own sampling rule: it says how many decided results the provider
// chooses to moderate and which. It is never a statement of what any regulator requires. The server enforces every rule; these save a round trip.

export type Version = {
  id: string; versionNo: number; effectiveFrom: string; basis: string; percentage: number | null; minCount: number | null; maxCount: number | null
  selectionMethod: string; riskFactors: string | null; extraTriggerQueries: number | null; extraLookback: number | null; extraPercentage: number | null
  rationale: string; createdAt: string
}
export type Rule = {
  id: string; ruleNo: string; name: string; scopeType: string; programmeId: string | null; programmeTitle: string | null; cohortId: string | null
  cohortName: string | null; assessmentType: string | null; status: string; retiredReason: string | null; current: Version | null; versions: Version[]
}
export type DrawRow = {
  id: string; drawNo: string; ruleNo: string | null; ruleName: string | null; versionNo: number; drawnOn: string; decidedFrom: string; decidedTo: string
  eligibleCount: number; requiredCount: number; selectedCount: number; status: string; decision: string | null
}
export type DrawItem = {
  id: string; attemptId: string; attemptNo: string | null; instrumentTitle: string | null; learnerName: string | null; assessorId: string; assessorName: string | null
  decision: string | null; riskScore: number; riskFactors: string | null; selected: boolean; selectedReason: string | null; targetNote: string | null
  moderationNo: string | null; moderationOutcome: string | null; moderationResolved: boolean
}
export type DrawAction = { id: string; itemId: string | null; description: string; ownerName: string; dueOn: string | null; status: string; closedOn: string | null; closedNote: string | null }
export type Draw = {
  id: string; drawNo: string; ruleId: string; ruleNo: string | null; ruleName: string | null; versionNo: number; ruleSnapshot: string; drawnOn: string; decidedFrom: string
  decidedTo: string; seed: number; eligibleCount: number; requiredCount: number; selectedCount: number; status: string; drawnBy: string; decision: string | null
  decisionNote: string | null; summary: string | null; voidReason: string | null; moderatedCount: number; items: DrawItem[]; actions: DrawAction[]
}

export const MIN_NOTE = 10
export const OWN_RULE_NOTE = "This is the provider's own sampling rule. It is not a regulatory requirement, and a draw does not show that any standard is met."
export const SCOPES: Record<string, string> = { ALL: "All moderated assessments", PROGRAMME: "One programme", ASSESSMENT_TYPE: "One assessment type", COHORT: "One cohort" }
export const BASES: Record<string, string> = { ALL: "Every eligible result", PERCENTAGE: "A percentage of eligible results" }
export const METHODS: Record<string, string> = { RANDOM: "Random", RISK_BASED: "Risk-based" }
export const REASONS: Record<string, string> = { ALL: "Every result", RANDOM: "Random pick", RISK: "Highest risk", TARGETED: "Chosen on purpose", EXTRA_AFTER_POOR: "Extra after queries" }
export const DECISIONS: Record<string, string> = { RESULTS_ACCEPTED: "Results accepted", SAMPLE_EXTENDED: "Sample extended", FURTHER_ACTION_REQUIRED: "Further action required" }
export const STATUS: Record<string, string> = { OPEN: "Open", CLOSED: "Closed", VOID: "Voided", ACTIVE: "Live", RETIRED: "Retired" }

const long = (s: string | null | undefined, n = MIN_NOTE) => (s ?? "").trim().length >= n
const num = (s: string): number | null => (s.trim() === "" ? null : Number(s))

export type SettingsForm = {
  effectiveFrom: string; basis: string; percentage: string; minCount: string; maxCount: string; selectionMethod: string; riskFactors: string
  extra: boolean; extraTriggerQueries: string; extraLookback: string; extraPercentage: string; rationale: string
}
export const emptySettings = (today: string): SettingsForm => ({
  effectiveFrom: today, basis: "PERCENTAGE", percentage: "", minCount: "", maxCount: "", selectionMethod: "RANDOM", riskFactors: "",
  extra: false, extraTriggerQueries: "", extraLookback: "", extraPercentage: "", rationale: "",
})
export const settingsFromVersion = (v: Version, today: string): SettingsForm => ({
  effectiveFrom: today, basis: v.basis, percentage: v.percentage === null ? "" : String(v.percentage), minCount: v.minCount === null ? "" : String(v.minCount),
  maxCount: v.maxCount === null ? "" : String(v.maxCount), selectionMethod: v.selectionMethod, riskFactors: v.riskFactors ?? "", extra: v.extraTriggerQueries !== null,
  extraTriggerQueries: v.extraTriggerQueries === null ? "" : String(v.extraTriggerQueries), extraLookback: v.extraLookback === null ? "" : String(v.extraLookback),
  extraPercentage: v.extraPercentage === null ? "" : String(v.extraPercentage), rationale: "",
})

export function settingsProblem(f: SettingsForm, latestFrom: string | null): string | null {
  if (!f.effectiveFrom) return "Choose the day this version starts"
  if (latestFrom && f.effectiveFrom <= latestFrom) return `A new version must start after the latest one (${latestFrom})`
  if (!BASES[f.basis]) return "Choose whether every result is moderated or a percentage"
  if (f.basis === "PERCENTAGE") {
    const p = num(f.percentage)
    if (p === null || Number.isNaN(p) || p <= 0 || p > 100) return "The percentage must be above 0 and no more than 100"
  }
  const min = num(f.minCount)
  const max = num(f.maxCount)
  if (min !== null && (Number.isNaN(min) || min < 0 || !Number.isInteger(min))) return "The minimum must be a whole number, 0 or more"
  if (max !== null && (Number.isNaN(max) || max < 1 || !Number.isInteger(max))) return "The maximum must be a whole number, at least 1"
  if (min !== null && max !== null && max < min) return "The maximum cannot be below the minimum"
  if (!METHODS[f.selectionMethod]) return "Choose random or risk-based selection"
  if (f.selectionMethod === "RISK_BASED" && !f.riskFactors.trim()) return "Say which risk factors you look at"
  if (f.extra) {
    const t = num(f.extraTriggerQueries)
    const l = num(f.extraLookback)
    const e = num(f.extraPercentage)
    if (t === null || l === null || e === null || [t, l, e].some(Number.isNaN)) return "Extra sampling needs the trigger, the look-back and the extra percentage"
    if (t < 1 || !Number.isInteger(t) || !Number.isInteger(l) || l < t) return "The look-back must be at least the trigger, and the trigger at least 1"
    if (e <= 0 || e > 100) return "The extra percentage must be above 0 and no more than 100"
  }
  if (!long(f.rationale)) return `Say why the rule is set this way (at least ${MIN_NOTE} characters)`
  return null
}

export function settingsBody(f: SettingsForm) {
  return {
    effectiveFrom: f.effectiveFrom, basis: f.basis, percentage: f.basis === "PERCENTAGE" ? num(f.percentage) : null, minCount: num(f.minCount), maxCount: num(f.maxCount),
    selectionMethod: f.selectionMethod, riskFactors: f.selectionMethod === "RISK_BASED" ? f.riskFactors.trim() : null,
    extraTriggerQueries: f.extra ? num(f.extraTriggerQueries) : null, extraLookback: f.extra ? num(f.extraLookback) : null, extraPercentage: f.extra ? num(f.extraPercentage) : null,
    rationale: f.rationale.trim(),
  }
}

export type RuleForm = { name: string; scopeType: string; programmeId: string; cohortId: string; assessmentType: string; settings: SettingsForm }
export const emptyRule = (today: string): RuleForm => ({ name: "", scopeType: "ALL", programmeId: "", cohortId: "", assessmentType: "", settings: emptySettings(today) })
export function ruleProblem(f: RuleForm): string | null {
  if (f.name.trim().length < 2) return "Give the rule a name"
  if (!SCOPES[f.scopeType]) return "Choose where the rule applies"
  if (f.scopeType === "PROGRAMME" && !f.programmeId) return "Choose a programme"
  if (f.scopeType === "COHORT" && !f.cohortId) return "Choose a cohort"
  if (f.scopeType === "ASSESSMENT_TYPE" && !f.assessmentType) return "Choose an assessment type"
  return settingsProblem(f.settings, null)
}
export const ruleBody = (f: RuleForm) => ({
  name: f.name.trim(), scopeType: f.scopeType, programmeId: f.scopeType === "PROGRAMME" ? f.programmeId : null, cohortId: f.scopeType === "COHORT" ? f.cohortId : null,
  assessmentType: f.scopeType === "ASSESSMENT_TYPE" ? f.assessmentType : null, settings: settingsBody(f.settings),
})

export function describeVersion(v: Version): string {
  const parts = [v.basis === "ALL" ? "Every eligible result" : `${v.percentage}% of eligible results`]
  if (v.minCount !== null) parts.push(`at least ${v.minCount}`)
  if (v.maxCount !== null) parts.push(`at most ${v.maxCount}`)
  parts.push(v.selectionMethod === "RISK_BASED" ? `risk-based (${v.riskFactors ?? ""})` : "random")
  if (v.extraTriggerQueries !== null) parts.push(`extra ${v.extraPercentage}% when ${v.extraTriggerQueries} of the last ${v.extraLookback} moderations raised a query`)
  return parts.join(", ")
}

export type DrawForm = { ruleId: string; decidedFrom: string; decidedTo: string; targeted: Record<string, string> }
export const emptyDraw = (today: string): DrawForm => ({ ruleId: "", decidedFrom: today, decidedTo: today, targeted: {} })
export function drawProblem(f: DrawForm, today: string): string | null {
  if (!f.ruleId) return "Choose a rule"
  if (!f.decidedFrom || !f.decidedTo) return "Choose the window of decisions to draw from"
  if (f.decidedFrom > f.decidedTo) return "The window must start on or before its end"
  if (f.decidedTo > today) return "The window cannot end in the future"
  for (const [, n] of Object.entries(f.targeted)) if (!long(n)) return `Give each chosen-on-purpose result a reason of at least ${MIN_NOTE} characters`
  return null
}
export const drawBody = (f: DrawForm) => ({
  ruleId: f.ruleId, decidedFrom: f.decidedFrom, decidedTo: f.decidedTo, targeted: Object.entries(f.targeted).map(([attemptId, note]) => ({ attemptId, note: note.trim() })),
})

export type CloseForm = { decision: string; note: string; confirmAlone: boolean }
export const emptyClose = (): CloseForm => ({ decision: "RESULTS_ACCEPTED", note: "", confirmAlone: false })
export function closeProblem(f: CloseForm, d: Pick<Draw, "selectedCount" | "moderatedCount" | "drawnBy" | "items" | "actions">, me: string | null): string | null {
  if (d.moderatedCount < d.selectedCount) return `${d.selectedCount - d.moderatedCount} picked result(s) have no moderation yet`
  if (!DECISIONS[f.decision]) return "Choose a decision"
  if (f.decision === "RESULTS_ACCEPTED" && d.items.some(i => i.selected && i.moderationOutcome === "QUERIED" && !i.moderationResolved)) return "A moderation query is not answered yet"
  if (f.decision === "FURTHER_ACTION_REQUIRED" && d.actions.length === 0) return "Record at least one corrective action first"
  if (!long(f.note)) return `Write the reason for your decision (at least ${MIN_NOTE} characters)`
  if (me && d.drawnBy === me && !f.confirmAlone) return "A second person should close this draw, or confirm that you are closing it yourself"
  return null
}

export type ActionForm = { itemId: string; description: string; ownerName: string; dueOn: string }
export const emptyAction = (): ActionForm => ({ itemId: "", description: "", ownerName: "", dueOn: "" })
export function actionProblem(f: ActionForm): string | null {
  if (!long(f.description)) return `Describe the action (at least ${MIN_NOTE} characters)`
  if (f.ownerName.trim().length < 2) return "Say who owns it"
  return null
}
export const actionBody = (f: ActionForm) => ({ itemId: f.itemId || null, description: f.description.trim(), ownerName: f.ownerName.trim(), dueOn: f.dueOn || null })
export const reasonProblem = (s: string): string | null => (long(s) ? null : `Give a reason of at least ${MIN_NOTE} characters`)
