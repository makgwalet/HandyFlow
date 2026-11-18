import { sendDepotItem } from "@depot/send";
import { sendDriverItem } from "@driver/send";
import { sendForecourtItem } from "@forecourt/send";
import { sendTruckStopItem } from "@truckstop/send";
import type { OutboxItem } from "./outbox";

/** Routes a queued item to the mode that knows how to send it. */
export function sendItem(item: OutboxItem): Promise<void> {
  switch (item.kind) {
    case "DIP_READING": case "RECEIVE_FUEL": return sendDepotItem(item);
    case "SHIFT_OPEN": case "SHIFT_CLOSE": return sendForecourtItem(item);
    case "STAY_BOOK": case "STAY_CHECK_IN": case "STAY_CHECK_OUT": return sendTruckStopItem(item);
    default: return sendDriverItem(item);
  }
}
