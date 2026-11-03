import { create } from 'zustand';
import type { SyncState } from '../types/domain';
import { useTransactionQueueStore } from './transactionQueueStore';

/**
 * Connectivity/sync status only — pending/failed counts are derived
 * directly from the transaction queue (see hooks/useQueueCounts.ts)
 * rather than tracked here, since maintaining a second counter in
 * parallel with the queue's own item statuses is a source of drift bugs
 * (a flow could enqueue without incrementing, or vice versa).
 */

interface SyncStore extends Pick<SyncState, 'status' | 'lastSyncedAt'> {
  setStatus: (status: SyncState['status']) => void;
  simulateSync: () => Promise<void>;
}

export const useSyncStore = create<SyncStore>((set) => ({
  status: 'online',
  lastSyncedAt: new Date().toISOString(),

  setStatus: (status) => set({ status }),

  simulateSync: async () => {
    set({ status: 'syncing' });
    await new Promise((r) => setTimeout(r, 900));
    useTransactionQueueStore.getState().confirmAllPending();
    set({ status: 'online', lastSyncedAt: new Date().toISOString() });
  },
}));
