// Taking a patient's record out on paper (patch 0191, CLINIC-DEC-009). A restricted record opened by break-glass must have the
// print recorded for the reviewer before it goes to paper; someone with standing access to the record needs nothing extra.
import { apiClient } from "../../api/client"

export interface RestrictionStatus { restricted: boolean; canAccess: boolean; breakGlassUntil?: string | null }

/** FREE: not restricted, or the person has standing access. RECORD: a break-glass session is open, so the print is recorded. REFUSE: no access at all. */
export function printDecision(s: RestrictionStatus | null | undefined): "FREE" | "RECORD" | "REFUSE" {
  if (!s || !s.restricted) return "FREE"
  if (!s.canAccess) return "REFUSE"
  return s.breakGlassUntil ? "RECORD" : "FREE"
}

export interface PrintCheck { ok: boolean; message?: string }

export const REFUSED = "This record is restricted. Printing it needs permission to print restricted records and an open break-glass session."

/**
 * Checks the record can go to paper and, for a break-glass session, records that it did. Call it after the click and before printing.
 * If the status cannot be read, nothing is printed: an unknown record is treated as restricted.
 */
export async function allowPrint(patientId: string, document: string): Promise<PrintCheck> {
  let status: RestrictionStatus
  try {
    const r: any = await apiClient.get(`/api/v1/clinic/patients/${patientId}/restriction`)
    status = (r?.data?.data ?? r?.data) as RestrictionStatus
  } catch { return { ok: false, message: "Could not check whether this record is restricted, so nothing was printed." } }
  const d = printDecision(status)
  if (d === "FREE") return { ok: true }
  if (d === "REFUSE") return { ok: false, message: REFUSED }
  try {
    await apiClient.post(`/api/v1/clinic/patients/${patientId}/break-glass/print`, { document })
    return { ok: true }
  } catch (e: any) {
    return { ok: false, message: typeof e?.response?.data?.message === "string" ? e.response.data.message : REFUSED }
  }
}
