import { create } from 'zustand';
import * as Crypto from 'expo-crypto';

/**
 * Mirrors the queue envelope from handyfuel.txt section 22 exactly:
 *   { clientRequestId, operation, createdAt, userId, deviceId, payload, status }
 *
 * This is in-memory only — it resets on app restart. A later phase swaps
 * the storage for expo-sqlite (already a project dependency) without
 * changing this shape, since the shape is what the backend sync contract
 * needs to match (see BACKEND_GAPS.md — no idempotency contract exists
 * server-side yet, so CONFIRMED/CONFLICT below are never reachable today).
 */

export type QueueStatus = 'PENDING' | 'UPLOADING' | 'CONFIRMED' | 'CONFLICT';

export interface QueueItem {
  clientRequestId: string;
  operation: 'FUEL_DISPATCH' | 'TANK_DIP' | 'DELIVERY_COMPLETE' | 'FUEL_RECEIPT' | 'SHIFT_CLOSE';
  createdAt: string;
  userId: string;
  payload: Record<string, unknown>;
  status: QueueStatus;
}

interface QueueStore {
  items: QueueItem[];
  enqueue: (operation: QueueItem['operation'], userId: string, payload: Record<string, unknown>) => QueueItem;
  confirmAllPending: () => void;
}

export const useTransactionQueueStore = create<QueueStore>((set) => ({
  items: [],
  enqueue: (operation, userId, payload) => {
    const item: QueueItem = {
      clientRequestId: Crypto.randomUUID(),
      operation,
      createdAt: new Date().toISOString(),
      userId,
      payload,
      status: 'PENDING',
    };
    set((s) => ({ items: [item, ...s.items] }));
    return item;
  },
  confirmAllPending: () =>
    set((s) => ({
      items: s.items.map((i) => (i.status === 'PENDING' ? { ...i, status: 'CONFIRMED' } : i)),
    })),
}));
