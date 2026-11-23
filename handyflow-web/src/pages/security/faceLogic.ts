// src/pages/security/faceLogic.ts
import { getCompanyZone } from "./companyZone"
//
// Pure wording for the guard's face-data card. The server never matches faces; it only holds the template the guard app sent, with the
// date the guard consented (empty for templates stored before consent was recorded).
export interface FaceStatus { stored: boolean; consentAt: string | null }

const date = (iso: string) => new Date(iso).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric", timeZone: getCompanyZone() })

/** Valid status from the server, or null if the response is not the expected shape. */
export function readStatus(x: unknown): FaceStatus | null {
  const o = x as { stored?: unknown; consentAt?: unknown } | null
  if (!o || typeof o.stored !== "boolean") return null
  return { stored: o.stored, consentAt: typeof o.consentAt === "string" && o.consentAt ? o.consentAt : null }
}

export function statusLine(s: FaceStatus): string {
  if (!s.stored) return "No face data is held for this guard."
  if (s.consentAt) return `A face template is held. The guard consented on ${date(s.consentAt)}.`
  return "A face template is held. It was stored before consent was recorded, so there is no consent date."
}

export type Tone = "ok" | "warn" | "neutral"
export const statusTone = (s: FaceStatus): Tone => (!s.stored ? "neutral" : s.consentAt ? "ok" : "warn")
export const statusLabel = (s: FaceStatus): string => (!s.stored ? "None held" : s.consentAt ? "Held, with consent" : "Held, no consent date")

export const REMOVE_WARNING = "This deletes the guard's face template for good. The guard can enrol again from the app, with consent."
