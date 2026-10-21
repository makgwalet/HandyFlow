// Integrations: lead capture keys (send leads in) and webhooks (tell your systems what happens). Mirrors the server's checks so mistakes show up before saving.
import type { LeadSource } from "./pipeline.logic"

export interface CaptureKey { id: string; name: string; keyPrefix: string; defaultSource: LeadSource; createdAt: string; lastUsedAt: string | null; active: boolean }
export type WebhookEvent = "LEAD_CREATED" | "STAGE_CHANGED" | "OPPORTUNITY_WON" | "LEAD_CONVERTED"
export const WEBHOOK_EVENTS: { id: WebhookEvent; label: string }[] = [
  { id: "LEAD_CREATED", label: "A lead is added" }, { id: "STAGE_CHANGED", label: "A lead changes stage" }, { id: "OPPORTUNITY_WON", label: "An opportunity is won" }, { id: "LEAD_CONVERTED", label: "A lead becomes a customer" },
]
export const eventLabel = (e: string) => WEBHOOK_EVENTS.find(x => x.id === e)?.label ?? (e === "PING" ? "Test message" : e)
export interface Webhook { id: string; name: string; url: string; events: WebhookEvent[]; active: boolean; suspended: boolean; consecutiveFailures: number; lastDeliveryAt: string | null }
export interface WebhookDelivery { id: string; webhookId: string; webhookName: string; event: string; outcome: "DONE" | "FAILED"; statusCode: number | null; detail: string | null; sentAt: string }
export interface WebhookForm { name: string; url: string; events: WebhookEvent[]; active: boolean }
export const EMPTY_WEBHOOK: WebhookForm = { name: "", url: "", events: ["LEAD_CREATED"], active: true }
export const toWebhookForm = (w: Webhook): WebhookForm => ({ name: w.name, url: w.url, events: w.events, active: w.active })
export const MAX_KEYS = 10, MAX_WEBHOOKS = 10

export function keyProblem(name: string): string | null {
  if (!name.trim()) return "Name the key."
  if (name.trim().length > 120) return "The name can be at most 120 characters."
  return null
}
const PRIVATE_HOST = /^(localhost|.*\.localhost|.*\.local|.*\.internal|.*\.lan)$/i
const PRIVATE_IPV4 = /^(127\.|10\.|192\.168\.|169\.254\.|0\.|172\.(1[6-9]|2\d|3[01])\.|100\.(6[4-9]|[7-9]\d|1[01]\d|12[0-7])\.)/
/** Why the address cannot be used, or null. The server checks again, including where the name leads. */
export function urlProblem(raw: string): string | null {
  const u = raw.trim()
  if (!u) return "Give the address to send to."
  if (u.length > 500) return "The address can be at most 500 characters."
  let url: URL
  try { url = new URL(u) } catch { return "That address is not valid." }
  if (url.protocol !== "https:") return "The address must start with https://."
  if (url.username || url.password) return "The address cannot contain a user name or password."
  const host = url.hostname.replace(/^\[|\]$/g, "")
  if (PRIVATE_HOST.test(host) || (!host.includes(".") && !host.includes(":")) || PRIVATE_IPV4.test(host) || host === "::1" || /^f[cd]/i.test(host) && host.includes(":") || /^fe80:/i.test(host)) return "That address points inside a private network."
  return null
}
export function webhookProblem(f: WebhookForm): string | null {
  if (!f.name.trim()) return "Name the webhook."
  if (f.name.trim().length > 120) return "The name can be at most 120 characters."
  const u = urlProblem(f.url)
  if (u) return u
  if (f.events.length === 0) return "Choose at least one event."
  return null
}
export const webhookBody = (f: WebhookForm) => ({ name: f.name.trim(), url: f.url.trim(), events: f.events, active: f.active })
export const toggledEvent = (events: WebhookEvent[], e: WebhookEvent): WebhookEvent[] => (events.includes(e) ? events.filter(x => x !== e) : [...events, e])
export function webhookStatus(w: Webhook): string {
  if (w.suspended) return `Stopped after ${w.consecutiveFailures} failures in a row. Save it again to switch it back on.`
  if (!w.active) return "Off"
  return w.consecutiveFailures > 0 ? `On, but the last ${w.consecutiveFailures} ${w.consecutiveFailures === 1 ? "attempt" : "attempts"} failed` : "On"
}
export const keyStatus = (k: CaptureKey) => (!k.active ? "Revoked" : k.lastUsedAt ? `Last used ${new Date(k.lastUsedAt).toLocaleDateString("en-ZA", { dateStyle: "medium" })}` : "Not used yet")
/** An example call a developer can copy, with the key left for them to fill in. */
export const captureExample = (origin: string) =>
  `curl -X POST ${origin}/api/v1/public/crm/leads \\\n  -H "Content-Type: application/json" \\\n  -H "X-Crm-Key: <your key>" \\\n  -d '{"name":"Thandi Nkosi","company":"Cape Wines","email":"thandi@example.com","phone":"082 123 4567","message":"Please send a quote","source":"WEBSITE"}'`
