// Files attached to an employee relations case. Mirrors ErEvidenceRules on the server, which checks the file itself.
export interface CaseFile { id: string; fileName: string; contentType: string; fileSizeBytes: number; evidenceType: string; uploadedByName: string | null; createdAt: string }

export const MAX_BYTES = 15 * 1024 * 1024
export const MAX_PER_CASE = 50
export const ACCEPTED = ["pdf", "png", "jpg", "jpeg", "docx", "xlsx", "txt", "mp3", "m4a", "wav"]
export const ACCEPT_ATTR = ACCEPTED.map(e => `.${e}`).join(",")

export const KINDS: { value: string; label: string }[] = [
  { value: "STATEMENT", label: "Statement" }, { value: "DOCUMENT", label: "Document" }, { value: "EMAIL", label: "Email" },
  { value: "PHOTO", label: "Photograph" }, { value: "RECORDING", label: "Recording" }, { value: "OTHER", label: "Other" },
]

export const kindLabel = (k: string) => KINDS.find(x => x.value === k)?.label ?? k

export const extensionOf = (name: string) => {
  const dot = name.lastIndexOf(".")
  return dot < 0 || dot === name.length - 1 ? "" : name.slice(dot + 1).toLowerCase()
}

export function fileProblem(file: { name: string; size: number } | null, kind: string, active: number, open: boolean): string {
  if (!open) return "This case is closed. Files cannot be added to it."
  if (!kind) return "Say what kind of evidence this is."
  if (!file) return "Choose the file."
  if (file.size <= 0) return "The file is empty."
  if (file.size > MAX_BYTES) return `The file is too large (${MAX_BYTES / (1024 * 1024)} MB at most).`
  if (!ACCEPTED.includes(extensionOf(file.name))) return `That type of file is not accepted. Use ${ACCEPTED.map(e => e.toUpperCase()).join(", ")}.`
  if (active >= MAX_PER_CASE) return `A case can hold at most ${MAX_PER_CASE} files.`
  return ""
}

export function removeProblem(reason: string): string {
  if (!reason.trim()) return "Give the reason for removing the file."
  if (reason.trim().length > 300) return "The reason is too long (300 characters at most)."
  return ""
}

export function sizeText(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
