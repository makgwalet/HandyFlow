// Pure helpers for documents attached to an inbound receipt (delivery note, damage photos, packing list). The server checks them again.

export const DOCUMENT_TYPES: { value: string; label: string }[] = [
  { value: "DELIVERY_NOTE", label: "Delivery note" },
  { value: "DAMAGE_PHOTO", label: "Photo of damage" },
  { value: "PACKING_LIST", label: "Packing list" },
  { value: "ASN_DOCUMENT", label: "Advance shipping notice" },
  { value: "OTHER", label: "Other" },
]

export const MAX_DOCUMENT_BYTES = 20 * 1024 * 1024

export const documentTypeLabel = (v: string) => DOCUMENT_TYPES.find(t => t.value === v)?.label ?? v

const OFFICE = /officedocument|msword|ms-excel/

/** Null when the file can be attached, else what is wrong. */
export function fileProblem(f: { name: string; size: number; type: string } | null): string | null {
  if (!f) return "Choose a file."
  if (f.size === 0) return "That file is empty."
  if (f.size > MAX_DOCUMENT_BYTES) return "That file is over 20 MB."
  const t = f.type.toLowerCase()
  if (!(t.startsWith("image/") || t === "application/pdf" || t.startsWith("text/") || OFFICE.test(t) || t === "")) return "Attach a photo, PDF, text or Office document."
  return null
}

export function fileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
