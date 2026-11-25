// How the work queue is filtered and where each item leads. The server decides what is in the queue and in what order.

export type QueueItem = {
  type: string; severity: string; clientId: string | null; clientName: string | null; title: string; detail: string; dueDate: string | null; tab: string
}

export const TYPE_LABELS: Record<string, string> = {
  DEADLINE: "Deadlines", DOCUMENT_REVIEW: "Documents to review", DOCUMENT_EXPIRY: "Documents expiring",
  APPROVAL: "Approvals", CATALOGUE_REVIEW: "Catalogue reviews", ASSESSMENT: "Assessments open", CASE: "Cases",
  REGULATORY_REVIEW: "Regulatory changes to review", REGULATORY_IMPACT: "Regulatory changes to deal with",
  QUALITY_REVIEW: "Reviews to finish", QUALITY_REWORK: "Work sent back",
  PORTAL_QUERY: "Client queries", DOCUMENT_REQUEST: "Documents we asked for",
}
export const typeLabel = (t: string) => TYPE_LABELS[t] ?? t

export const SEVERITY_LABELS: Record<string, string> = { HIGH: "Urgent", MEDIUM: "Soon", LOW: "When you can" }
export const severityLabel = (s: string) => SEVERITY_LABELS[s] ?? s

/** Where to go to deal with an item. Catalogue reviews are not about one client. */
export function linkFor(i: Pick<QueueItem, "clientId" | "tab" | "type">): string {
  if (i.type === "REGULATORY_REVIEW") return "/complianceservices/regulatory"
  if (i.type === "CATALOGUE_REVIEW" || !i.clientId) return "/complianceservices/catalogue"
  return `/complianceservices/clients/${i.clientId}?tab=${i.tab}`
}

/** The types that actually appear, with how many of each, in a steady order. */
export function typeCounts(items: QueueItem[]): [string, number][] {
  const order = Object.keys(TYPE_LABELS)
  const counts = new Map<string, number>()
  for (const i of items) counts.set(i.type, (counts.get(i.type) ?? 0) + 1)
  return [...counts.entries()].sort((a, b) => (order.indexOf(a[0]) + 1 || 99) - (order.indexOf(b[0]) + 1 || 99))
}

export function filterItems(items: QueueItem[], type: string, search: string): QueueItem[] {
  const q = search.trim().toLowerCase()
  return items.filter(i => (type === "ALL" || i.type === type) && (!q || `${i.clientName ?? ""} ${i.title} ${i.detail}`.toLowerCase().includes(q)))
}
