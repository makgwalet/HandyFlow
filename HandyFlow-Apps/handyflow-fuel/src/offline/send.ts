import { sendDepotItem } from "@depot/send";
import { sendDriverItem } from "@driver/send";
import type { OutboxItem } from "./outbox";

/** Routes a queued item to the mode that knows how to send it. */
export function sendItem(item: OutboxItem): Promise<void> {
  return item.kind === "DIP_READING" || item.kind === "RECEIVE_FUEL" ? sendDepotItem(item) : sendDriverItem(item);
}
