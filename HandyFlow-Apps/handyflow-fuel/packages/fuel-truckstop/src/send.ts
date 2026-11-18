import { api } from "@/api/client";
import type { OutboxItem } from "@/offline/outbox";

const BASE = "/api/v1/fuel/truck-stop/stays";

/**
 * Sends one queued truck stop write with `item.id` as clientRequestId. The server returns the first result for a repeated id,
 * so a lost reply never checks a truck in or out twice and never raises a second invoice. Nothing here prices a stay or takes
 * payment: the server prices it from the stay's own rate and the dates, and the invoice stays with the financial integration.
 */
export async function sendTruckStopItem(item: OutboxItem): Promise<void> {
  const p = item.payload;
  const meta = { clientRequestId: item.id, capturedAt: item.recordedAt };
  switch (item.kind) {
    case "STAY_BOOK":
      await api(BASE, { method: "POST", body: { ...(p.body as object), ...meta } });
      return;
    case "STAY_CHECK_IN":
      await api(`${BASE}/${String(p.stayId)}/check-in`, { method: "POST", body: meta });
      return;
    case "STAY_CHECK_OUT":
      // The date the truck left, fixed when it was captured, so a late sync does not add nights.
      await api(`${BASE}/${String(p.stayId)}/check-out`, { method: "POST", body: { date: p.date ?? null, ...meta } });
      return;
    default:
      throw new Error(`Not a truck stop item: ${item.kind}`);
  }
}

export const stayWaiting = (pending: OutboxItem[], stayId: string): OutboxItem | undefined =>
  pending.find((i) => (i.kind === "STAY_CHECK_IN" || i.kind === "STAY_CHECK_OUT") && i.payload.stayId === stayId);
