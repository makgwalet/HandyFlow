import { api } from "@/api/client";
import type { OutboxItem } from "@/offline/outbox";

const BASE = "/api/v1/fuel/forecourt";

/**
 * Sends one queued forecourt write. `item.id` goes as clientRequestId and the time it was captured on the phone goes as
 * capturedAt (informational: the server keeps its own times). The server returns the first answer for a repeated id, so a
 * lost reply never opens or closes a shift twice. A refusal (the meter moved on, the shift was already closed by someone
 * else) is parked as FAILED by the outbox and kept on the server's conflict list.
 */
export async function sendForecourtItem(item: OutboxItem): Promise<void> {
  const p = item.payload;
  const body = { ...(p.body as object), clientRequestId: item.id, capturedAt: item.recordedAt };
  switch (item.kind) {
    case "SHIFT_OPEN":
      await api(`${BASE}/shifts`, { method: "POST", body });
      return;
    case "SHIFT_CLOSE":
      // A shift opened offline is known only by the id its open was queued under.
      await api(p.openRequestId ? `${BASE}/shifts/by-request/${String(p.openRequestId)}/close` : `${BASE}/shifts/${String(p.shiftId)}/close`, { method: "POST", body });
      return;
    default:
      throw new Error(`Not a forecourt item: ${item.kind}`);
  }
}

/** A close for this shift is already waiting to send. */
export const closeWaiting = (pending: OutboxItem[], target: { shiftId?: string; openRequestId?: string }): boolean =>
  pending.some((i) => i.kind === "SHIFT_CLOSE" && ((target.shiftId && i.payload.shiftId === target.shiftId) || (target.openRequestId && i.payload.openRequestId === target.openRequestId)));
