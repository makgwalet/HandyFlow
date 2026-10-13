// Document Studio in the patient file (patch 0203): numbered documents, the kept copy, sending one to the patient, reissuing a prescription.
import { apiClient } from "../../api/client"

export interface IssuedDoc {
  id: string; number: string; type: string; typeLabel: string; consultationId?: string | null
  issuedAt: string; status: "ISSUED" | "VOIDED"; voidReason?: string | null; copyKept: boolean
  lastSentAt?: string | null; lastSentTo?: string | null
}

const BASE = "/api/v1/clinic"

/** The server's message from a failed request, including one whose body is a PDF blob (downloads ask for blobs). */
export async function errorMessage(e: any, fallback: string): Promise<string> {
  const data = e?.response?.data
  if (data && typeof data.text === "function") {
    try { const parsed = JSON.parse(await data.text()); return parsed?.message || fallback } catch { return fallback }
  }
  return data?.message || fallback
}

export function saveBlob(data: BlobPart, fileName: string) {
  const url = URL.createObjectURL(new Blob([data], { type: "application/pdf" }))
  const a = document.createElement("a")
  a.href = url; a.download = fileName; a.click()
  setTimeout(() => URL.revokeObjectURL(url), 60_000)
}

/** The number in a "PRESCRIPTION RX-2026-000001 was issued…" refusal, so the question can name it. */
export function standingNumber(message: string): string | null {
  const m = /\b([A-Z]{2}-\d{4}-\d{6})\b/.exec(message)
  return m ? m[1] : null
}

/**
 * Downloads the visit's prescription. The first download issues it; later ones return the same numbered copy. If the medicines
 * changed since, the server refuses (409) and `askReason` is asked for a reason; with one, the prescription is reissued and the
 * earlier copy withdrawn. Returns a message for the screen, or null when the file was saved.
 */
export async function downloadPrescription(consultationId: string, askReason: (number: string | null, message: string) => Promise<string | null>): Promise<string | null> {
  try {
    const res = await apiClient.get(`${BASE}/consultations/${consultationId}/prescription-pdf`, { responseType: "blob" })
    saveBlob(res.data, `prescription-${consultationId}.pdf`)
    return null
  } catch (e: any) {
    const message = await errorMessage(e, "The prescription could not be downloaded.")
    if (e?.response?.status !== 409) return message
    const reason = await askReason(standingNumber(message), message)
    if (!reason) return null
    try {
      const res = await apiClient.post(`${BASE}/consultations/${consultationId}/reissued-prescription-pdf`, { reason }, { responseType: "blob" })
      saveBlob(res.data, `prescription-${consultationId}.pdf`)
      return null
    } catch (e2: any) {
      return errorMessage(e2, "The prescription could not be reissued.")
    }
  }
}

export async function visitDocuments(consultationId: string, type?: string): Promise<{ patientId: string; documents: IssuedDoc[] }> {
  const r = await apiClient.get(`${BASE}/consultations/${consultationId}/issued-documents`, { params: type ? { type } : {} })
  const d = r.data?.data ?? r.data
  return { patientId: d?.patientId ?? "", documents: Array.isArray(d?.documents) ? d.documents : [] }
}

export async function patientDocuments(patientId: string): Promise<IssuedDoc[]> {
  const r = await apiClient.get(`${BASE}/patients/${patientId}/issued-documents`)
  const d = r.data?.data ?? r.data
  return Array.isArray(d) ? d : []
}

export async function emailToPatient(patientId: string, documentId: string): Promise<{ recipient: string; number: string }> {
  const r = await apiClient.post(`${BASE}/patients/${patientId}/issued-documents/${documentId}/email`)
  const d = r.data?.data ?? r.data
  return { recipient: d?.recipient ?? "", number: d?.number ?? "" }
}

export async function downloadKeptCopy(patientId: string, doc: Pick<IssuedDoc, "id" | "number">) {
  const res = await apiClient.get(`${BASE}/patients/${patientId}/issued-documents/${doc.id}/pdf`, { responseType: "blob" })
  saveBlob(res.data, `${doc.number}.pdf`)
}

/** What a row says about delivery: "Sent to a@b.c on 13 Oct" or "Not sent". */
export function sentLine(doc: Pick<IssuedDoc, "lastSentAt" | "lastSentTo">): string {
  if (!doc.lastSentAt) return "Not sent"
  const day = new Date(doc.lastSentAt).toLocaleDateString("en-ZA", { day: "numeric", month: "short", year: "numeric" })
  return `Sent to ${doc.lastSentTo ?? "the patient"} on ${day}`
}

/** A document can be sent only when it stands and its copy was kept (copies are kept from patch 0203 on). */
export const canSend = (doc: Pick<IssuedDoc, "status" | "copyKept">) => doc.status === "ISSUED" && doc.copyKept
