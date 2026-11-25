// Wording and small checks for client queries and document requests. The server decides what is allowed; these only help the screen say it.

export type StaffQuery = { id: string; clientId: string; clientName: string | null; subject: string; status: string; raisedByName: string | null; createdAt: string; lastMessageAt: string; messageCount: number }
export type StaffMessage = { authorType: string; authorId: string | null; authorName: string | null; body: string; createdAt: string }
export type StaffQueryDetail = { query: StaffQuery; messages: StaffMessage[] }
export type StaffRequest = { id: string; clientId: string; clientName: string | null; title: string; description: string | null; documentType: string | null; dueDate: string | null; status: string; requestedAt: string; closedAt: string | null; closeNote: string | null; late: boolean }

export const MIN_NOTE = 10
export const MAX_BODY = 4000

export const QUERY_STATUS: Record<string, string> = { OPEN: "Waiting for us", ANSWERED: "Answered", CLOSED: "Closed" }
export const queryStatusLabel = (s: string) => QUERY_STATUS[s] ?? s

export const REQUEST_STATUS: Record<string, string> = { OPEN: "Asked for", RECEIVED: "File received", CLOSED: "Closed", CANCELLED: "Cancelled" }
export const requestStatusLabel = (s: string) => REQUEST_STATUS[s] ?? s

export const isLiveRequest = (r: Pick<StaffRequest, "status">) => r.status === "OPEN" || r.status === "RECEIVED"

/** Queries that need the practice first, then the rest by most recent message. */
export function sortQueries(list: StaffQuery[]): StaffQuery[] {
  const rank = (s: string) => (s === "OPEN" ? 0 : s === "ANSWERED" ? 1 : 2)
  return [...list].sort((a, b) => rank(a.status) - rank(b.status) || b.lastMessageAt.localeCompare(a.lastMessageAt))
}

export function filterQueries(list: StaffQuery[], status: string, search: string): StaffQuery[] {
  const q = search.trim().toLowerCase()
  return list.filter(x => (status === "ALL" || x.status === status) && (!q || `${x.clientName ?? ""} ${x.subject}`.toLowerCase().includes(q)))
}

/** The reason for closing or cancelling must say something. Empty space does not count. */
export const noteProblem = (note: string): string => (note.trim().length >= MIN_NOTE ? "" : `Say why, in at least ${MIN_NOTE} characters`)
export const bodyProblem = (body: string): string => (body.trim() === "" ? "Write a message first" : body.trim().length > MAX_BODY ? `Keep it under ${MAX_BODY} characters` : "")

export const UPLOAD_EXTENSIONS = ["pdf", "jpg", "jpeg", "png", "xlsx", "docx", "csv"]
export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024

/** Looks at a file before it is sent. The server checks again; this only saves the wait. */
export function fileProblem(name: string, size: number): string {
  const dot = name.lastIndexOf(".")
  const ext = dot < 0 ? "" : name.slice(dot + 1).toLowerCase()
  if (!UPLOAD_EXTENSIONS.includes(ext)) return "Use a PDF, JPG, PNG, XLSX, DOCX or CSV file"
  if (size <= 0) return "That file is empty"
  if (size > MAX_UPLOAD_BYTES) return "That file is too large. The most you can upload is 10 MB"
  return ""
}
