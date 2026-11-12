// Pure helpers for delivery variances and proof-of-delivery evidence.
export interface Variance {
  id: string; deliveryId: string; receiptNumber?: string | null; driverName?: string | null; vehicleReg?: string | null
  loadedLitres: number; deliveredLitres: number; varianceLitres: number; variancePct: number; tolerancePct: number
  status: "OPEN" | "INVESTIGATING" | "CLOSED"; decision?: "EXPLAINED" | "WRITTEN_OFF" | null; note?: string | null; detectedAt: string
}
export interface VarianceList { toleranceSet: boolean; variances: Variance[] }

export const VARIANCE_STATUS_LABEL: Record<string, string> = { OPEN: "Open", INVESTIGATING: "Investigating", CLOSED: "Closed" }
export const DECISION_LABEL: Record<string, string> = { EXPLAINED: "Explained", WRITTEN_OFF: "Written off" }

/** "-100 L short" / "50 L over". */
export function varianceLabel(v: { varianceLitres: number }): string {
  const n = Math.abs(v.varianceLitres).toLocaleString("en-ZA", { maximumFractionDigits: 2 })
  return v.varianceLitres < 0 ? `${n} L short` : v.varianceLitres > 0 ? `${n} L over` : "No difference"
}

export const openCount = (l: VarianceList | undefined) => (l?.variances ?? []).filter(v => v.status !== "CLOSED").length

export function validateNote(note: string): string | null { return note.trim().length >= 5 ? null : "Give a note of at least 5 characters." }

export interface Evidence {
  id: string; deliveryId: string; kind: "PHOTO" | "SIGNATURE" | "DOCUMENT" | "GPS"; fileRef?: string | null
  latitude?: number | null; longitude?: number | null; accuracyM?: number | null; note?: string | null
  capturedAt: string; capturedBy?: string | null; status: "ACTIVE" | "SUPERSEDED"; supersededReason?: string | null
  verifiedBy?: string | null; verifiedAt?: string | null; locked: boolean
  fileStored?: boolean; contentType?: string | null; fileSizeBytes?: number | null
}
export interface EvidenceForm { kind: Evidence["kind"]; fileRef: string; latitude: string; longitude: string; accuracyM: string; note: string }
export const emptyEvidence = (): EvidenceForm => ({ kind: "PHOTO", fileRef: "", latitude: "", longitude: "", accuracyM: "", note: "" })
export const KIND_LABEL: Record<string, string> = { PHOTO: "Photo", SIGNATURE: "Signature", DOCUMENT: "Document", GPS: "GPS point" }

export function validateEvidence(f: EvidenceForm): string | null {
  if (f.kind === "GPS") {
    const lat = Number(f.latitude), lon = Number(f.longitude)
    if (f.latitude.trim() === "" || f.longitude.trim() === "" || !Number.isFinite(lat) || !Number.isFinite(lon)) return "Enter a latitude and a longitude."
    if (Math.abs(lat) > 90) return "Latitude must be between -90 and 90."
    if (Math.abs(lon) > 180) return "Longitude must be between -180 and 180."
    if (f.accuracyM.trim() !== "" && !(Number(f.accuracyM) >= 0)) return "Accuracy must be 0 or more."
    return null
  }
  return f.fileRef.trim() ? null : "Enter the file link or reference."
}
export function toEvidenceRequest(f: EvidenceForm) {
  const gps = f.kind === "GPS"
  return {
    kind: f.kind, fileRef: gps ? null : f.fileRef.trim(), latitude: gps ? Number(f.latitude) : null, longitude: gps ? Number(f.longitude) : null,
    accuracyM: gps && f.accuracyM.trim() !== "" ? Number(f.accuracyM) : null, note: f.note.trim() || null,
  }
}

/** The capturer is never offered Verify, and a replaced or already verified record cannot be verified or replaced. Server enforces all of it. */
export const canVerify = (e: Evidence, me?: string) => e.status === "ACTIVE" && !e.locked && !!me && e.capturedBy !== me
export const canReplace = (e: Evidence) => e.status === "ACTIVE" && !e.locked
export const hasVerifiedProof = (list: Evidence[]) => list.some(e => e.status === "ACTIVE" && e.locked)
