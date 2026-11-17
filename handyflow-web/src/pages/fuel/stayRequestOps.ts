// Overnight stays that customers asked for through the portal. Staff approve (which books the stay) or decline.
export type StayRequest = {
  id: string; accountId: string; customerName: string; requestedByEmail: string | null; vehicleRegistration: string; driverName: string | null; driverPhone: string | null
  arrivalDate: string; departureDate: string; nights: number; status: "PENDING" | "APPROVED" | "DECLINED" | "CANCELLED"; note: string | null; decisionNote: string | null
  stayId: string | null; createdAt: string; decidedAt: string | null
}

export const REQUEST_FILTERS: [string, string][] = [["PENDING", "Waiting"], ["APPROVED", "Approved"], ["DECLINED", "Declined"], ["CANCELLED", "Cancelled"]]

export function onlyRequests(v: unknown): StayRequest[] {
  return Array.isArray(v) ? v.filter((r: any) => r && typeof r.id === "string" && typeof r.vehicleRegistration === "string") : []
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]
/** "2026-11-14" as "14 Nov". Read from the text, so the viewer's time zone cannot move the day. */
export function shortDay(iso: string): string {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso)
  return m ? `${Number(m[3])} ${MONTHS[Number(m[2]) - 1]}` : iso
}

export function stayText(r: Pick<StayRequest, "arrivalDate" | "departureDate" | "nights">): string {
  return `${shortDay(r.arrivalDate)} to ${shortDay(r.departureDate)} · ${r.nights} night${r.nights === 1 ? "" : "s"}`
}

/** Who to contact: the driver if given, else the customer's portal user. */
export function contactText(r: Pick<StayRequest, "driverName" | "driverPhone" | "requestedByEmail">): string {
  const parts = [r.driverName, r.driverPhone].filter(Boolean)
  return parts.length ? parts.join(" · ") : r.requestedByEmail ?? ""
}

export function pendingCount(rs: StayRequest[]): number { return rs.filter(r => r.status === "PENDING").length }
