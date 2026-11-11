// Pure helpers for warehousing webhooks. The server checks every address again, including where its name points when a message is sent.

export const EVENT_LABELS: Record<string, string> = {
  "inbound.received": "Goods received",
  "outbound.picking_started": "Picking started",
  "outbound.shipped": "Order shipped",
  "outbound.cancelled": "Order cancelled",
}

export function eventLabel(e: string): string {
  return e === "*" ? "All events" : EVENT_LABELS[e] ?? e
}

/** Null when the address looks usable, else what is wrong. Catches the common slips: https only, no password, nothing obviously inside a network. */
export function urlProblem(raw: string): string | null {
  const u = raw.trim()
  if (!u) return "Give the address to send to."
  if (u.length > 500) return "The address can be at most 500 characters."
  let url: URL
  try { url = new URL(u) } catch { return "That address is not valid." }
  if (url.protocol !== "https:") return "The address must start with https://"
  if (url.username || url.password) return "The address cannot contain a user name or password."
  const h = url.hostname.toLowerCase()
  const privateV4 = /^(10\.|127\.|0\.|192\.168\.|169\.254\.|172\.(1[6-9]|2\d|3[01])\.|100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.)/.test(h)
  if (h === "localhost" || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal") || h.endsWith(".lan") || privateV4 || h === "[::1]" || !h.includes(".") && !h.startsWith("[")) {
    return "That address points inside a private network."
  }
  return null
}

export type DeliveryStatus = "PENDING" | "RETRY" | "DELIVERED" | "DEAD"

export const DELIVERY_STATUS: Record<DeliveryStatus, { bg: string; fg: string; label: string }> = {
  PENDING: { bg: "var(--hf-info-soft)", fg: "var(--hf-info-text)", label: "Waiting" },
  RETRY: { bg: "var(--hf-warning-soft-strong)", fg: "var(--hf-warning-text-deep)", label: "Retrying" },
  DELIVERED: { bg: "var(--hf-success-soft)", fg: "var(--hf-success-text-strong)", label: "Delivered" },
  DEAD: { bg: "var(--hf-danger-soft)", fg: "var(--hf-danger-text)", label: "Failed" },
}

/** A failed or retrying delivery can be sent again by hand. */
export const canRedeliver = (s: DeliveryStatus) => s === "DEAD" || s === "RETRY"

/** The events picked in a form as the list the server wants: "*" alone when every event is ticked or "all" is chosen. */
export function chosenEvents(all: boolean, picked: string[], known: string[]): string[] {
  if (all || (known.length > 0 && known.every(k => picked.includes(k)))) return ["*"]
  return known.filter(k => picked.includes(k))
}
