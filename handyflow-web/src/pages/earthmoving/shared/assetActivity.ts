// One timeline for a machine: status changes, incidents, shifts and inspections, newest first.
import type { StatusEvent, Incident, OperatorLog, Inspection } from "./types"

export interface ActivityItem { key: string; at: string; kind: "STATUS" | "INCIDENT" | "SHIFT" | "INSPECTION"; text: string }

const word = (s: string | null) => (s ? s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, " ") : "—")
const RESULT: Record<string, string> = { PASS: "passed", DEFECTS: "passed with defects", UNSAFE: "found unsafe" }
const TYPE: Record<string, string> = { PRE_USE: "Pre-use", HANDOVER: "Handover", RETURN: "Return" }

export function activityFeed(input: { events: StatusEvent[]; incidents: Incident[]; logs: OperatorLog[]; inspections: Inspection[] }, limit = 30): ActivityItem[] {
  const items: ActivityItem[] = []
  for (const e of input.events)
    items.push({ key: "s" + e.id, at: e.happenedAt, kind: "STATUS", text: `${e.fromStatus ? word(e.fromStatus) + " → " : ""}${word(e.toStatus)}${e.reason ? `: ${e.reason}` : ""}` })
  for (const i of input.incidents)
    items.push({ key: "i" + i.id, at: i.reportedAt, kind: "INCIDENT", text: `${word(i.severity)} ${word(i.type).toLowerCase()}: ${i.title}${i.status === "RESOLVED" ? " (resolved)" : ""}` })
  for (const l of input.logs)
    items.push({ key: "l" + l.id, at: l.startedAt, kind: "SHIFT", text: `Shift${l.operatorName ? ` by ${l.operatorName}` : ""}${l.siteName ? ` at ${l.siteName}` : ""}${l.hoursLogged != null ? `, ${l.hoursLogged} logged` : l.endedAt ? "" : " (in progress)"}` })
  for (const n of input.inspections)
    items.push({ key: "n" + n.id, at: n.performedAt, kind: "INSPECTION", text: `${TYPE[n.inspectionType] ?? "Pre-use"} inspection ${RESULT[n.result] ?? n.result}` })
  return items.sort((a, b) => Date.parse(b.at) - Date.parse(a.at)).slice(0, limit)
}
