// Work order rules for the workshop screen (mirrors the server's status table and permissions) and its request bodies.
export const STATUS_LABEL: Record<string, string> = { OPEN: "Open", QUOTED: "Awaiting approval", APPROVED: "Approved", IN_PROGRESS: "In progress", ON_HOLD: "On hold", COMPLETED: "Completed", CANCELLED: "Cancelled" }
export const TYPE_LABEL: Record<string, string> = { SERVICE: "Service", REPAIR: "Repair", ACCIDENT_REPAIR: "Accident repair", TYRE: "Tyres", BATTERY: "Battery", ROADWORTHY_PREPARATION: "Roadworthy preparation", OTHER: "Other" }
export const PRIORITIES = ["LOW", "NORMAL", "HIGH", "URGENT"]
export const LINE_TYPES = ["LABOUR", "PART", "SUBLET", "OTHER"]
export const LINE_LABEL: Record<string, string> = { LABOUR: "Labour", PART: "Part", SUBLET: "Sublet", OTHER: "Other" }

export type Step = "QUOTE" | "APPROVE" | "REJECT" | "START" | "HOLD" | "RESUME" | "COMPLETE" | "CANCEL"
export interface StepOption { step: Step; label: string; admin: boolean; needsText: boolean }
const S = (step: Step, label: string, admin = false, needsText = false): StepOption => ({ step, label, admin, needsText })

/** What can be done to an order in this status. Approving and rejecting a quotation need FLEET_ADMIN. */
export function stepsFor(status: string): StepOption[] {
  switch (status) {
    case "OPEN": return [S("QUOTE", "Record quotation"), S("START", "Start work"), S("CANCEL", "Cancel", false, true)]
    case "QUOTED": return [S("APPROVE", "Approve", true), S("REJECT", "Reject", true, true), S("QUOTE", "Revise quotation"), S("CANCEL", "Cancel", false, true)]
    case "APPROVED": return [S("START", "Start work"), S("CANCEL", "Cancel", false, true)]
    case "IN_PROGRESS": return [S("HOLD", "Put on hold", false, true), S("COMPLETE", "Complete"), S("CANCEL", "Cancel", false, true)]
    case "ON_HOLD": return [S("RESUME", "Resume"), S("CANCEL", "Cancel", false, true)]
    default: return []
  }
}
/** Costs can be added while the order is live; the server rejects changes once it is completed or cancelled. */
export const canEditLines = (status: string) => ["OPEN", "QUOTED", "APPROVED", "IN_PROGRESS", "ON_HOLD"].includes(status)

const bad = (v: string) => v.trim() !== "" && !(Number(v) >= 0)
const num = (v: string) => (v.trim() === "" ? null : Number(v))

export interface NewOrderForm { vehicleId: string; type: string; title: string; priority: string; technicianId: string; supplier: string; scheduledFor: string }
export const emptyOrder = (): NewOrderForm => ({ vehicleId: "", type: "SERVICE", title: "", priority: "NORMAL", technicianId: "", supplier: "", scheduledFor: "" })
export function orderProblem(f: NewOrderForm) { if (!f.vehicleId) return "Choose the vehicle."; if (!f.title.trim()) return "Say what the work is."; return null }
export const orderBody = (f: NewOrderForm) => ({ vehicleId: f.vehicleId, type: f.type, title: f.title.trim(), priority: f.priority, technicianId: f.technicianId || null, supplier: f.supplier.trim() || null, scheduledFor: f.scheduledFor || null })

export interface QuoteForm { amount: string; supplier: string; quotationRef: string; validUntil: string }
export const emptyQuote = (): QuoteForm => ({ amount: "", supplier: "", quotationRef: "", validUntil: "" })
export function quoteProblem(f: QuoteForm) { return !(Number(f.amount) > 0) ? "Enter the quoted amount." : null }
export const quoteBody = (f: QuoteForm) => ({ amount: Number(f.amount), supplier: f.supplier.trim() || null, quotationRef: f.quotationRef.trim() || null, validUntil: f.validUntil || null })

export interface LineForm { lineType: string; description: string; quantity: string; unitCost: string; technicianId: string }
export const emptyLine = (): LineForm => ({ lineType: "PART", description: "", quantity: "1", unitCost: "", technicianId: "" })
/** Labour may leave the unit cost blank (the technician's hourly rate is used); every other line needs one. */
export function lineProblem(f: LineForm) {
  if (!f.description.trim()) return "Describe the cost."
  if (!(Number(f.quantity) > 0)) return f.lineType === "LABOUR" ? "Enter the hours." : "Enter the quantity."
  if (bad(f.unitCost)) return "The cost must be zero or more."
  if (f.unitCost.trim() === "" && !(f.lineType === "LABOUR" && f.technicianId)) return f.lineType === "LABOUR" ? "Choose the technician or enter a rate." : "Enter the cost."
  return null
}
export const lineBody = (f: LineForm) => ({ lineType: f.lineType, description: f.description.trim(), quantity: Number(f.quantity), unitCost: num(f.unitCost), technicianId: f.technicianId || null })

export interface CompleteForm { odometer: string; invoiceRef: string; completionNotes: string; nextServiceKm: string }
export const emptyComplete = (): CompleteForm => ({ odometer: "", invoiceRef: "", completionNotes: "", nextServiceKm: "" })
/** A service must record the odometer (it resets the service interval). */
export function completeProblem(type: string, f: CompleteForm) {
  if (type === "SERVICE" && f.odometer.trim() === "") return "Enter the odometer reading: it resets the service interval."
  if (bad(f.odometer) || bad(f.nextServiceKm)) return "Readings must be zero or more."
  return null
}
export const completeBody = (f: CompleteForm) => ({ odometer: num(f.odometer), invoiceRef: f.invoiceRef.trim() || null, completionNotes: f.completionNotes.trim() || null, nextServiceKm: num(f.nextServiceKm) })

/** "R 1 200 over the quote" / "R 300 under the quote" / null with no quotation. */
export function varianceText(variance: number | null | undefined): string | null {
  if (variance == null) return null
  const r = `R ${Math.abs(Math.round(variance)).toLocaleString("en-ZA")}`
  return variance > 0 ? `${r} over the quote` : variance < 0 ? `${r} under the quote` : "On the quote"
}

export interface TechForm { name: string; phone: string; email: string; hourlyRate: string }
export const emptyTech = (): TechForm => ({ name: "", phone: "", email: "", hourlyRate: "" })
export function techProblem(f: TechForm) { if (!f.name.trim()) return "Enter the technician's name."; if (bad(f.hourlyRate)) return "The hourly rate must be zero or more."; return null }
export const techBody = (f: TechForm) => ({ name: f.name.trim(), phone: f.phone.trim() || null, email: f.email.trim() || null, hourlyRate: num(f.hourlyRate), active: true })

// ── Attachments: quotations, invoices, job cards and photos ─────────────────────────────────────────────
export const ATTACHMENT_TYPES = ["QUOTATION", "INVOICE", "JOB_CARD", "PHOTO", "OTHER"]
export const ATTACHMENT_LABEL: Record<string, string> = { QUOTATION: "Quotation", INVOICE: "Invoice", JOB_CARD: "Job card", PHOTO: "Photo", OTHER: "Other" }
export const ATTACHMENT_MAX_BYTES = 15 * 1024 * 1024
export const ATTACHMENT_MAX_COUNT = 50
export const ATTACHMENT_MIME = ["application/pdf", "image/jpeg", "image/png", "image/webp", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"]

export function fileSizeText(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
/** What the server would refuse, caught before the upload: type, file kind, size and the 50-per-order limit. */
export function attachmentProblem(type: string, file: { name: string; type: string; size: number } | null, existing: number): string | null {
  if (!type) return "Choose what this file is."
  if (!file) return "Choose a file."
  if (existing >= ATTACHMENT_MAX_COUNT) return `A work order can have at most ${ATTACHMENT_MAX_COUNT} attachments.`
  if (!ATTACHMENT_MIME.includes(file.type)) return "Attach a PDF, an image (JPEG, PNG or WebP) or a Word (.docx) file."
  if (file.size > ATTACHMENT_MAX_BYTES) return `That file is ${fileSizeText(file.size)}; the limit is 15 MB.`
  if (file.size === 0) return "That file is empty."
  return null
}
/** The kind of file most likely wanted at this stage, to pre-select the type. */
export const suggestedAttachmentType = (status: string): string => (status === "OPEN" || status === "QUOTED" ? "QUOTATION" : status === "COMPLETED" ? "INVOICE" : status === "IN_PROGRESS" ? "PHOTO" : "")
