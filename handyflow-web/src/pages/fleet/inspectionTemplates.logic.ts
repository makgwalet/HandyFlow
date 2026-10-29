// What a person sets up for an inspection template, and the checks the screen makes before saving (the server checks them again).
export const INSPECTION_TYPES: [string, string][] = [
  ["DAILY", "Daily"], ["PRE_TRIP", "Pre-trip"], ["POST_TRIP", "Post-trip"], ["WEEKLY", "Weekly"], ["MONTHLY", "Monthly"],
  ["ROADWORTHY_PREPARATION", "Roadworthy preparation"], ["DRIVER_HANDOVER", "Driver handover"], ["RETURN_TO_FLEET", "Return to fleet"], ["OTHER", "Other"],
]
export const SEVERITIES: [string, string][] = [["LOW", "Low"], ["MEDIUM", "Medium"], ["HIGH", "High"], ["CRITICAL", "Critical"]]
export const VEHICLE_TYPES = ["SEDAN", "SUV", "BAKKIE", "TRUCK", "MINIBUS", "VAN", "MOTORCYCLE", "OTHER"]

export interface ItemForm { section: string; label: string; required: boolean; photoRequiredOnFail: boolean; severity: string }
export interface TemplateForm {
  name: string; inspectionType: string; description: string; intervalDays: string; appliesToVehicleType: string
  signatureRequired: boolean; active: boolean; items: ItemForm[]
}
export const emptyItem = (section = ""): ItemForm => ({ section, label: "", required: true, photoRequiredOnFail: false, severity: "MEDIUM" })
export const emptyTemplate = (): TemplateForm => ({
  name: "", inspectionType: "DAILY", description: "", intervalDays: "", appliesToVehicleType: "", signatureRequired: false, active: true, items: [emptyItem()],
})

interface ServerItem { section: string; label: string; required: boolean; photoRequiredOnFail: boolean; severity: string; sortOrder?: number }
interface ServerTemplate { name: string; inspectionType: string; description: string | null; intervalDays: number | null; appliesToVehicleType: string | null; signatureRequired: boolean; active: boolean; items: ServerItem[] }

export const formFrom = (t: ServerTemplate): TemplateForm => ({
  name: t.name, inspectionType: t.inspectionType, description: t.description ?? "", intervalDays: t.intervalDays == null ? "" : String(t.intervalDays),
  appliesToVehicleType: t.appliesToVehicleType ?? "", signatureRequired: t.signatureRequired, active: t.active,
  items: [...t.items].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0)).map(i => ({ section: i.section, label: i.label, required: i.required, photoRequiredOnFail: i.photoRequiredOnFail, severity: i.severity })),
})

export function templateProblem(f: TemplateForm): string | null {
  if (!f.name.trim()) return "Give the inspection a name."
  if (f.intervalDays !== "" && !(Number.isInteger(Number(f.intervalDays)) && Number(f.intervalDays) >= 1 && Number(f.intervalDays) <= 3650)) return "Repeat every must be a whole number of days from 1 to 3650."
  const items = f.items.filter(i => i.label.trim() || i.section.trim())
  if (items.length === 0) return "Add at least one checklist item."
  if (items.some(i => !i.section.trim() || !i.label.trim())) return "Every checklist item needs a section and a description."
  if (items.length > 200) return "A template can have at most 200 items."
  return null
}

export function templateBody(f: TemplateForm) {
  return {
    name: f.name.trim(), inspectionType: f.inspectionType, description: f.description.trim() || null,
    intervalDays: f.intervalDays === "" ? null : Number(f.intervalDays), appliesToVehicleType: f.appliesToVehicleType || null,
    signatureRequired: f.signatureRequired, active: f.active,
    items: f.items.filter(i => i.label.trim() || i.section.trim()).map(i => ({ section: i.section.trim(), label: i.label.trim(), required: i.required, photoRequiredOnFail: i.photoRequiredOnFail, severity: i.severity })),
  }
}

/** Moves item `from` to position `to` (used by the up and down buttons). */
export function moveItem<T>(items: T[], from: number, to: number): T[] {
  if (to < 0 || to >= items.length || from === to) return items
  const copy = [...items]; const [x] = copy.splice(from, 1); copy.splice(to, 0, x); return copy
}
