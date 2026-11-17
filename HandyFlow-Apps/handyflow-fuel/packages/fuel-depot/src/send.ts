import { api } from "@/api/client";
import type { OutboxItem } from "@/offline/outbox";

const tankPath = (tankId: unknown) => `/api/v1/fuel/tanks/${String(tankId)}`;

/**
 * Sends one queued depot write. `item.id` goes as clientRequestId: the server keeps a unique index on it for dip readings
 * and receipts, so a retry after a lost reply returns the first record instead of adding stock or a reading twice.
 */
export async function sendDepotItem(item: OutboxItem): Promise<void> {
  const p = item.payload;
  const body = { ...(p.body as object), clientRequestId: item.id };
  switch (item.kind) {
    case "DIP_READING":
      await api(`${tankPath(p.tankId)}/dip-readings`, { method: "POST", body });
      return;
    case "RECEIVE_FUEL":
      await api(`${tankPath(p.tankId)}/receive`, { method: "POST", body });
      return;
    default:
      throw new Error(`Not a depot item: ${item.kind}`);
  }
}
