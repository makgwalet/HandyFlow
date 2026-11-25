// Wording for the early warning list. The server decides the signs and the order; this only words them and builds links.

export type Signal = { code: string; severity: string; label: string; detail: string; tab: string }
export type ClientEntry = { clientId: string; clientName: string; riskRating: string | null; high: number; medium: number; low: number; signals: Signal[] }
export type Summary = { asOf: string; activeClients: number; flaggedClients: number; withHigh: number; clients: ClientEntry[] }

export const FILTERS: [string, string][] = [["ALL", "All flagged"], ["HIGH", "With urgent signs"]]
export const SEVERITY_LABELS: Record<string, string> = { HIGH: "Urgent", MEDIUM: "Soon", LOW: "Note" }
export const severityLabel = (s: string) => SEVERITY_LABELS[s] ?? s

export const filtered = (list: ClientEntry[], filter: string, search: string): ClientEntry[] => {
  const q = search.trim().toLowerCase()
  return list.filter(c => (filter === "ALL" || c.high > 0) && (!q || c.clientName.toLowerCase().includes(q)))
}

/** The tab on the client's page that deals with a sign. */
export const linkFor = (clientId: string, tab: string) => `/complianceservices/clients/${clientId}?tab=${tab}`

export function headline(s: Pick<Summary, "activeClients" | "flaggedClients" | "withHigh">): string {
  if (s.activeClients === 0) return "There are no active clients."
  if (s.flaggedClients === 0) return `None of the ${s.activeClients} active clients shows any of the signs below. That is not a statement that they comply.`
  return `${s.flaggedClients} of ${s.activeClients} active clients show signs of trouble${s.withHigh > 0 ? `, ${s.withHigh} with urgent ones` : ""}.`
}

export function counts(c: Pick<ClientEntry, "high" | "medium" | "low">): string {
  const parts: string[] = []
  if (c.high) parts.push(`${c.high} urgent`)
  if (c.medium) parts.push(`${c.medium} soon`)
  if (c.low) parts.push(`${c.low} to note`)
  return parts.join(", ")
}
