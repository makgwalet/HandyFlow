import { useMemo } from 'react';
import { useTransactionQueueStore } from '../store/transactionQueueStore';

/**
 * Sums FUEL_RECEIPT / FUEL_DISPATCH queue items for one tank within a
 * time window. This is what makes Shift Reconciliation's "Received" and
 * "Dispensed" numbers real rather than re-entered — the same
 * transactionQueueStore items created by the Dispense and Receive Fuel
 * flows in this session feed directly into the shift math.
 *
 * NOTE: the queue is in-memory only (see BACKEND_GAPS.md) and driven by
 * `createdAt`, the CLIENT clock — a real implementation needs the
 * backend's authoritative transaction timestamps once a sync endpoint
 * exists, or a shift spanning offline periods could total incorrectly.
 */
export function useShiftTotals(tankId: string | null, sinceISO: string | null, untilISO?: string) {
  const items = useTransactionQueueStore((s) => s.items);

  return useMemo(() => {
    if (!tankId || !sinceISO) return { received: 0, dispensed: 0 };

    let received = 0;
    let dispensed = 0;

    for (const item of items) {
      const payload = item.payload as Record<string, unknown>;
      if (payload.tankId !== tankId) continue;
      if (item.createdAt < sinceISO) continue;
      if (untilISO && item.createdAt > untilISO) continue;

      if (item.operation === 'FUEL_RECEIPT') {
        received += Number(payload.actualReceivedLitres ?? payload.waybillQuantityLitres ?? 0);
      } else if (item.operation === 'FUEL_DISPATCH') {
        dispensed += Number(payload.quantityLitres ?? 0);
      }
    }

    return { received, dispensed };
  }, [items, tankId, sinceISO, untilISO]);
}
