// Wording and checks for the qualification and programme catalogue. The server enforces every rule.
// No qualification, credit or level content is supplied: the provider records what its authority registered.

export type Qualification = {
  id: string; title: string; qualificationType: string; officialRef: string | null; saqaId: string | null; subFramework: string | null
  nqfLevel: number | null; credits: number | null; authorityId: string | null; status: string; retiredReason: string | null
  currentVersionNo: number | null; versions: number
}
export type QualificationVersion = {
  id: string; qualificationId: string; versionNo: number; status: string; registrationStatus: string; registrationStart: string | null
  registrationEnd: string | null; entryRequirements: string | null; exitOutcomes: string | null; rulesOfCombination: string | null
  sourceNote: string | null; effectiveFrom: string | null
}
export type Programme = {
  id: string; code: string; title: string; programmeType: string; trainingActivity: string; deliveryMode: string
  qualificationId: string | null; qualificationTitle: string | null; courseId: string | null; description: string | null
  status: string; retiredReason: string | null; approvedVersionNo: number | null; liveOfferings: number; advertisableAsAccredited: boolean
}
export type Module = { id: string; seq: number; title: string; moduleType: string; credits: number | null; notionalHours: number | null; required: boolean; learningOutcomes: string | null }
export type ProgrammeVersion = {
  id: string; versionNo: number; status: string; qualificationVersionId: string | null; learningHours: number | null; credits: number | null
  summary: string | null; effectiveFrom: string | null; approvedAt: string | null; approvalOverrideReason: string | null; creditsNote: string | null; modules: Module[]
}
export type Offering = {
  id: string; programmeId: string; siteId: string; siteName: string; status: string; offeredFrom: string | null; coverageNote: string | null
  covered: boolean; coverageOutcome: string; coverageMessage: string; withdrawnReason: string | null
}
export type ProgrammeDetail = { programme: Programme; versions: ProgrammeVersion[]; offerings: Offering[] }
export type QualificationDetail = { qualification: Qualification; versions: QualificationVersion[] }

export const MIN_REASON = 10

export const QUALIFICATION_TYPES: Record<string, string> = {
  QUALIFICATION: "Qualification", PART_QUALIFICATION: "Part qualification", SKILLS_PROGRAMME: "Skills programme",
  UNIT_STANDARD: "Unit standard", PROFESSIONAL_DEVELOPMENT: "Professional development", OTHER: "Other",
}
export const REGISTRATION_STATUSES: Record<string, string> = {
  REGISTERED: "Registered", RE_REGISTERED: "Re-registered", NOT_YET_REGISTERED: "Not yet registered", LAPSED: "Lapsed", UNKNOWN: "Not known",
}
export const ACTIVITIES: Record<string, string> = { ACCREDITED: "Accredited", NON_ACCREDITED: "Non-accredited", WORKPLACE_BASED: "Workplace-based" }
export const PROGRAMME_TYPES: Record<string, string> = {
  QUALIFICATION: "Qualification", PART_QUALIFICATION: "Part qualification", SKILLS_PROGRAMME: "Skills programme",
  UNIT_STANDARD_PROGRAMME: "Unit standard programme", NON_ACCREDITED_COURSE: "Non-accredited course",
  PROFESSIONAL_DEVELOPMENT: "Professional development", CUSTOM_CORPORATE: "Custom corporate programme",
}
/** Which programme types suit each kind of training activity. */
export const TYPES_BY_ACTIVITY: Record<string, string[]> = {
  ACCREDITED: ["QUALIFICATION", "PART_QUALIFICATION", "SKILLS_PROGRAMME", "UNIT_STANDARD_PROGRAMME"],
  NON_ACCREDITED: ["NON_ACCREDITED_COURSE", "PROFESSIONAL_DEVELOPMENT", "CUSTOM_CORPORATE"],
  WORKPLACE_BASED: ["QUALIFICATION", "PART_QUALIFICATION", "SKILLS_PROGRAMME", "UNIT_STANDARD_PROGRAMME", "CUSTOM_CORPORATE"],
}
export const DELIVERY_MODES: Record<string, string> = { CLASSROOM: "Classroom", PRACTICAL: "Practical", ONLINE: "Online", BLENDED: "Blended", WORKPLACE: "Workplace" }
export const MODULE_TYPES: Record<string, string> = { KNOWLEDGE: "Knowledge", PRACTICAL: "Practical", WORKPLACE: "Workplace" }

export const STATUS: Record<string, string> = {
  DRAFT: "Draft", ACTIVE: "Active", RETIRED: "Retired", CURRENT: "Current", SUPERSEDED: "Superseded", APPROVED: "Approved", WITHDRAWN: "Withdrawn",
}
export const statusLabel = (s: string) => STATUS[s] ?? s
export const label = (m: Record<string, string>, k: string | null | undefined) => (k ? m[k] ?? k : "—")

const long = (s: string | null | undefined, n: number) => (s ?? "").trim().length >= n
const blank = (s: string) => (s.trim() === "" ? null : s.trim())
const intOrNull = (s: string) => (s.trim() === "" ? null : Number(s))
const isWhole = (n: number | null, min: number, max = Number.MAX_SAFE_INTEGER) => n === null || (Number.isInteger(n) && n >= min && n <= max)

/** What the programme can honestly be called. Accredited only when an active offering sits at a currently covered site. */
export function accreditedClaim(p: Pick<Programme, "trainingActivity" | "advertisableAsAccredited" | "status">): string {
  if (p.trainingActivity !== "ACCREDITED") return label(ACTIVITIES, p.trainingActivity)
  if (p.advertisableAsAccredited) return "Accredited, covered at a site where it is offered"
  return "Accredited programme, but not covered anywhere it is offered today"
}

// ---- qualifications
export type QualificationForm = { title: string; qualificationType: string; officialRef: string; saqaId: string; subFramework: string; nqfLevel: string; credits: string; authorityId: string }
export const emptyQualification: QualificationForm = { title: "", qualificationType: "QUALIFICATION", officialRef: "", saqaId: "", subFramework: "", nqfLevel: "", credits: "", authorityId: "" }
export function qualificationProblem(f: QualificationForm): string | null {
  if (f.title.trim().length < 3) return "Give the qualification a title of at least 3 characters"
  if (!isWhole(intOrNull(f.nqfLevel), 1, 10)) return "The NQF level is a whole number from 1 to 10"
  if (!isWhole(intOrNull(f.credits), 0)) return "Credits must be a whole number, zero or more"
  return null
}
export const qualificationBody = (f: QualificationForm) => ({
  title: f.title.trim(), qualificationType: f.qualificationType, officialRef: blank(f.officialRef), saqaId: blank(f.saqaId), subFramework: blank(f.subFramework),
  nqfLevel: intOrNull(f.nqfLevel), credits: intOrNull(f.credits), authorityId: f.authorityId || null,
})

export type QualificationVersionForm = { registrationStatus: string; registrationStart: string; registrationEnd: string; entryRequirements: string; exitOutcomes: string; rulesOfCombination: string; sourceNote: string; effectiveFrom: string }
export const emptyQualificationVersion: QualificationVersionForm = { registrationStatus: "UNKNOWN", registrationStart: "", registrationEnd: "", entryRequirements: "", exitOutcomes: "", rulesOfCombination: "", sourceNote: "", effectiveFrom: "" }
export function qualificationVersionProblem(f: QualificationVersionForm): string | null {
  if (f.registrationStart && f.registrationEnd && f.registrationEnd < f.registrationStart) return "The registration cannot end before it starts"
  return null
}
export const qualificationVersionBody = (f: QualificationVersionForm) => ({
  registrationStatus: f.registrationStatus, registrationStart: f.registrationStart || null, registrationEnd: f.registrationEnd || null,
  entryRequirements: blank(f.entryRequirements), exitOutcomes: blank(f.exitOutcomes), rulesOfCombination: blank(f.rulesOfCombination),
  sourceNote: blank(f.sourceNote), effectiveFrom: f.effectiveFrom || null,
})

// ---- programmes
export type ProgrammeForm = { code: string; title: string; trainingActivity: string; programmeType: string; deliveryMode: string; qualificationId: string; description: string }
export const emptyProgramme: ProgrammeForm = { code: "", title: "", trainingActivity: "ACCREDITED", programmeType: "SKILLS_PROGRAMME", deliveryMode: "CLASSROOM", qualificationId: "", description: "" }
export function programmeProblem(f: ProgrammeForm): string | null {
  if (f.code.trim().length < 2) return "Give the programme a code of at least 2 characters"
  if (f.title.trim().length < 3) return "Give the programme a title of at least 3 characters"
  if (!(TYPES_BY_ACTIVITY[f.trainingActivity] ?? []).includes(f.programmeType)) return `A ${label(ACTIVITIES, f.trainingActivity).toLowerCase()} programme cannot be a ${label(PROGRAMME_TYPES, f.programmeType).toLowerCase()}`
  if (f.trainingActivity === "ACCREDITED" && !f.qualificationId) return "An accredited programme needs the qualification it is accredited against"
  return null
}
export const programmeBody = (f: ProgrammeForm) => ({
  code: f.code.trim(), title: f.title.trim(), programmeType: f.programmeType, trainingActivity: f.trainingActivity,
  deliveryMode: f.deliveryMode, qualificationId: f.qualificationId || null, courseId: null, description: blank(f.description),
})
/** Changing the activity must move the type to one that suits it. */
export function typeForActivity(activity: string, current: string): string {
  const ok = TYPES_BY_ACTIVITY[activity] ?? []
  return ok.includes(current) ? current : ok[0] ?? current
}

export type VersionForm = { qualificationVersionId: string; learningHours: string; credits: string; summary: string; effectiveFrom: string }
export const emptyVersion: VersionForm = { qualificationVersionId: "", learningHours: "", credits: "", summary: "", effectiveFrom: "" }
export function versionProblem(f: VersionForm): string | null {
  const h = f.learningHours.trim() === "" ? null : Number(f.learningHours)
  if (h !== null && (!Number.isFinite(h) || h < 0)) return "Learning hours must be zero or more"
  if (!isWhole(intOrNull(f.credits), 0)) return "Credits must be a whole number, zero or more"
  return null
}
export const versionBody = (f: VersionForm) => ({
  qualificationVersionId: f.qualificationVersionId || null, learningHours: f.learningHours.trim() === "" ? null : Number(f.learningHours),
  credits: intOrNull(f.credits), summary: blank(f.summary), effectiveFrom: f.effectiveFrom || null,
})

export type ModuleForm = { title: string; moduleType: string; credits: string; notionalHours: string; required: boolean; learningOutcomes: string }
export const emptyModule: ModuleForm = { title: "", moduleType: "KNOWLEDGE", credits: "", notionalHours: "", required: true, learningOutcomes: "" }
export function moduleProblem(f: ModuleForm): string | null {
  if (f.title.trim().length < 3) return "Give the module a title of at least 3 characters"
  if (!isWhole(intOrNull(f.credits), 0)) return "Credits must be a whole number, zero or more"
  const h = f.notionalHours.trim() === "" ? null : Number(f.notionalHours)
  if (h !== null && (!Number.isFinite(h) || h < 0)) return "Notional hours must be zero or more"
  return null
}
export const moduleBody = (f: ModuleForm) => ({
  title: f.title.trim(), moduleType: f.moduleType, credits: intOrNull(f.credits),
  notionalHours: f.notionalHours.trim() === "" ? null : Number(f.notionalHours), required: f.required, learningOutcomes: blank(f.learningOutcomes),
})

export function reasonProblem(reason: string): string | null {
  return long(reason, MIN_REASON) ? null : `Say why, in at least ${MIN_REASON} characters`
}

/** Credits stated on modules against the credits stated on the version. A note only: the provider decides what is right. */
export function creditTotal(mods: Pick<Module, "credits" | "required">[]): { all: number; required: number } {
  return mods.reduce((a, m) => ({ all: a.all + (m.credits ?? 0), required: a.required + (m.required ? m.credits ?? 0 : 0) }), { all: 0, required: 0 })
}
