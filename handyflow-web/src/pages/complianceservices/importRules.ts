// What the import screen says. The server reads and checks the file; these are only wording and an early look at the file's name and size.

export type ImportBatch = {
  id: string; kind: string; fileName: string; status: string; totalRows: number; okRows: number; duplicateRows: number; rejectedRows: number; importedRows: number
  createdAt: string; committedAt: string | null; discardNote: string | null
}
export type ImportRow = { rowNumber: number; fields: string[]; status: string; message: string | null; targetId: string | null }
export type ImportDetail = { batch: ImportBatch; columns: string[]; rows: ImportRow[]; rowsTruncated: boolean }

export const MAX_BYTES = 2_000_000
export const KINDS: Record<string, string> = { CLIENTS: "Clients", REGISTRATIONS: "Registrations" }
export const kindLabel = (k: string) => KINDS[k] ?? k

export const BATCH_STATUS: Record<string, string> = { PREVIEWED: "Checked, not imported", COMMITTED: "Imported", DISCARDED: "Set aside" }
export const batchStatus = (s: string) => BATCH_STATUS[s] ?? s

export const ROW_STATUS: Record<string, string> = { OK: "Ready", DUPLICATE: "Already there", REJECTED: "Problem", IMPORTED: "Created" }
export const rowStatus = (s: string) => ROW_STATUS[s] ?? s

export const COLUMN_LABELS: Record<string, string> = {
  name: "Name", email: "Email", phone: "Phone", notes: "Notes", client: "Client", authority: "Authority", type: "Type", number: "Number", issued: "Issued", expiry: "Expires",
}
export const columnLabel = (c: string) => COLUMN_LABELS[c] ?? c

export const TEMPLATE: Record<string, string> = {
  CLIENTS: "name,email,phone,notes",
  REGISTRATIONS: "client,authority,type,number,issued,expiry,notes",
}

/** What is wrong with the file before it is sent, or null. */
export function fileProblem(file: { name: string; size: number } | null): string | null {
  if (!file) return "Choose a file"
  if (!file.name.toLowerCase().endsWith(".csv")) return "Save the spreadsheet as a CSV file and choose that"
  if (file.size === 0) return "The file is empty"
  if (file.size > MAX_BYTES) return "The file is larger than 2 MB"
  return null
}

export const summary = (b: ImportBatch): string =>
  b.status === "COMMITTED"
    ? `${b.importedRows} created, ${b.duplicateRows} already there, ${b.rejectedRows} with a problem, out of ${b.totalRows} rows`
    : `${b.okRows} ready, ${b.duplicateRows} already there, ${b.rejectedRows} with a problem, out of ${b.totalRows} rows`

export const discardProblem = (n: string): string | null => (n.trim().length >= 10 ? null : "Give a reason of at least 10 characters")
