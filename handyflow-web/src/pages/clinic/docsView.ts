// Documents register: wording, filters and the checks made before a file is sent.
export interface RegisterItem {
  origin: "STORED" | "RECORD"; id: string; docType: string; source: string; title: string; date?: string | null; notes?: string | null
  fileName?: string | null; sizeBytes?: number | null; addedBy?: string | null; consultationId?: string | null
  downloadPath: string; contentType?: string | null; removable: boolean
}

export const TYPE_LABEL: Record<string, string> = {
  SICK_NOTE: "Sick note", REFERRAL: "Referral", OUTSIDE_REPORT: "Outside report", IMAGING: "Imaging", LETTER: "Letter",
  PAPER_NOTES: "Paper notes", CONSENT_FORM: "Consent form", OTHER: "Other", VISIT_SUMMARY: "Visit summary", PRESCRIPTION: "Prescription", LAB_REPORT: "Lab report",
}
export const typeLabel = (t: string) => TYPE_LABEL[t] ?? t.toLowerCase().replace(/_/g, " ")

/** The types a person can pick when uploading; sick notes and referrals are only written when one is issued. */
export const UPLOAD_TYPES = ["OUTSIDE_REPORT", "IMAGING", "LETTER", "PAPER_NOTES", "CONSENT_FORM", "OTHER"] as const

export const sourceLabel = (i: RegisterItem) => i.origin === "RECORD" ? "From the record" : i.source === "ISSUED" ? "Issued here" : "Uploaded"

export function sizeLabel(bytes?: number | null): string {
  if (bytes == null) return ""
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export const MAX_BYTES = 10 * 1024 * 1024
const OK_TYPES = ["application/pdf", "image/jpeg", "image/png"]

/** Why a chosen file cannot be sent, or null. The server checks the real content again. */
export function fileProblem(f: { name: string; size: number; type: string } | null | undefined): string | null {
  if (!f) return "Choose a file."
  if (f.size === 0) return "That file is empty."
  if (f.size > MAX_BYTES) return `That file is ${sizeLabel(f.size)}. The most that can be uploaded is 10 MB.`
  if (f.type && !OK_TYPES.includes(f.type)) return "Only PDF, JPEG and PNG files can be uploaded."
  return null
}

export function uploadProblem(a: { file: { name: string; size: number; type: string } | null; type: string; title: string; date: string }, today: string): string | null {
  const fp = fileProblem(a.file); if (fp) return fp
  if (!a.type) return "Choose what kind of document this is."
  if (!a.title.trim()) return "Give the document a title."
  if (!/^\d{4}-\d{2}-\d{2}$/.test(a.date)) return "Enter the date on the document."
  if (a.date > today) return "The date on the document cannot be in the future."
  return null
}

/** The kinds present in the register, in a fixed order, with how many of each. */
export function kindCounts(items: RegisterItem[]): { type: string; count: number }[] {
  const order = Object.keys(TYPE_LABEL)
  const m = new Map<string, number>()
  for (const i of items) m.set(i.docType, (m.get(i.docType) ?? 0) + 1)
  return [...m.entries()].map(([type, count]) => ({ type, count })).sort((a, b) => (order.indexOf(a.type) + 1 || 99) - (order.indexOf(b.type) + 1 || 99))
}
