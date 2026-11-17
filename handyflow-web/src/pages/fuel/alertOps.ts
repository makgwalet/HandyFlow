// Who is told about a Fuel alert besides administrators. Nobody chosen means administrators only, as before.
import { userLabel, type UserOption } from "./scopeOps"

export type AlertTopic = { topic: string; label: string; userIds: string[]; excludeAdmins?: boolean }

/** Other tests answer every URL with generic data; keep only rows that really look like alert topics. */
export function onlyTopics(raw: unknown): AlertTopic[] {
  if (!Array.isArray(raw)) return []
  return raw.filter((t: any) => t && typeof t.topic === "string" && Array.isArray(t.userIds)) as AlertTopic[]
}

export function topicSummary(t: AlertTopic, users: UserOption[]): string {
  if (t.userIds.length === 0) return "Administrators only"
  const names = t.userIds.map(id => userLabel(id, users)).join(", ")
  return t.excludeAdmins ? names + " (administrators left off; they still see the alert in the app)" : "Administrators and " + names
}
