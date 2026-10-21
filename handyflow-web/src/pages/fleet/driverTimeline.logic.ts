// A driver's history, built only from what is really recorded: when they were registered, documents added or replaced, and accidents.
export type TimelineKind = "REGISTERED" | "DOCUMENT" | "ACCIDENT"
export interface TimelineItem { key: string; at: string; kind: TimelineKind; title: string; detail: string; tone: "ok" | "warn" | "bad" | "none" }
export interface TimelineDoc { id: string; type: string; title: string | null; version: number; status?: string; uploadedAt?: string; fileName: string }
export interface TimelineAccident { id: string; referenceNumber: string; occurredAt: string; status: string }

const DOC_LABEL: Record<string, string> = {
  PHOTO: "Photo", DRIVING_LICENCE: "Driving licence", PRDP: "PrDP", MEDICAL_CERTIFICATE: "Medical certificate", ID_COPY: "ID copy",
  CONTRACT: "Employment contract", TRAINING_CERTIFICATE: "Training certificate", OTHER: "Other document",
}
const humanise = (s: string) => s.replace(/_/g, " ").toLowerCase().replace(/^./, c => c.toUpperCase())

/** Newest first. A replaced document shows as "replaced"; the first of its kind shows as "added". */
export function buildDriverTimeline(input: { createdAt?: string | null; documents: TimelineDoc[]; accidents: TimelineAccident[] }): TimelineItem[] {
  const items: TimelineItem[] = []
  if (input.createdAt) items.push({ key: "registered", at: input.createdAt, kind: "REGISTERED", title: "Registered as a driver", detail: "", tone: "none" })
  for (const d of input.documents) {
    if (!d.uploadedAt) continue
    const name = d.title?.trim() || DOC_LABEL[d.type] || humanise(d.type)
    items.push({ key: `doc-${d.id}`, at: d.uploadedAt, kind: "DOCUMENT", title: `${name} ${d.version > 1 ? "replaced" : "added"}`, detail: `${d.fileName}${d.version > 1 ? ` · version ${d.version}` : ""}`, tone: "ok" })
  }
  for (const a of input.accidents) items.push({ key: `acc-${a.id}`, at: a.occurredAt, kind: "ACCIDENT", title: `Accident ${a.referenceNumber}`, detail: humanise(a.status), tone: "bad" })
  return items.sort((a, b) => b.at.localeCompare(a.at))
}
