// What a driver's file holds, and what each kind of document needs before the server will take it (the server checks the same).
export type DriverDocType = "DRIVING_LICENCE" | "PRDP" | "ID_COPY" | "CONTRACT" | "MEDICAL_CERTIFICATE" | "TRAINING_CERTIFICATE" | "OTHER"
export type DriverDocGroup = "documents" | "medical"

export const DRIVER_DOC_LABEL: Record<DriverDocType, string> = {
  DRIVING_LICENCE: "Driving licence", PRDP: "PrDP", ID_COPY: "ID copy", CONTRACT: "Employment contract",
  MEDICAL_CERTIFICATE: "Medical certificate", TRAINING_CERTIFICATE: "Training certificate", OTHER: "Other",
}
/** The kinds shown on each tab. The photo is handled by the picture box, not listed here. */
export const GROUP_TYPES: Record<DriverDocGroup, DriverDocType[]> = {
  documents: ["DRIVING_LICENCE", "PRDP", "ID_COPY", "CONTRACT", "OTHER"],
  medical: ["MEDICAL_CERTIFICATE", "TRAINING_CERTIFICATE"],
}
export const MAX_DOC_BYTES = 15 * 1024 * 1024
export const DOC_FILE_TYPES = ["application/pdf", "image/jpeg", "image/png", "image/webp"]

export interface DriverDoc {
  id: string; type: string; title: string | null; documentNumber: string | null; issueDate: string | null; expiryDate: string | null
  version: number; fileName: string; expired: boolean; daysToExpiry: number | null; uploadedAt?: string; status?: string
}
export interface UploadForm { type: string; title: string; issueDate: string; expiryDate: string }

export function uploadProblem(f: UploadForm, file: { type: string; size: number } | null): string | null {
  if (!f.type) return "Choose the kind of document."
  if (f.type === "TRAINING_CERTIFICATE" && !f.title.trim()) return "Name the course."
  if (f.type === "MEDICAL_CERTIFICATE" && !f.expiryDate) return "A medical certificate needs an expiry date."
  if (f.issueDate && f.expiryDate && f.expiryDate < f.issueDate) return "The expiry date can't be before the issue date."
  if (!file) return "Choose a file."
  if (!DOC_FILE_TYPES.includes(file.type)) return "Use a PDF, JPEG, PNG or WebP file."
  if (file.size > MAX_DOC_BYTES) return "That file is over 15 MB."
  return null
}

/** Documents of the tab's kinds, with the ones expiring first at the top and undated ones last. */
export function docsFor(docs: DriverDoc[], group: DriverDocGroup): DriverDoc[] {
  const kinds = new Set<string>(GROUP_TYPES[group])
  return docs.filter(d => kinds.has(d.type)).sort((a, b) => (a.daysToExpiry ?? Infinity) - (b.daysToExpiry ?? Infinity) || a.type.localeCompare(b.type))
}
export const docTone = (d: Pick<DriverDoc, "daysToExpiry">): "ok" | "warn" | "bad" | "none" =>
  d.daysToExpiry == null ? "none" : d.daysToExpiry < 0 ? "bad" : d.daysToExpiry < 30 ? "warn" : "ok"
/** "Defensive driving" for a training certificate, otherwise the kind of document. */
export const docName = (d: Pick<DriverDoc, "type" | "title">): string => d.title?.trim() || DRIVER_DOC_LABEL[d.type as DriverDocType] || d.type
