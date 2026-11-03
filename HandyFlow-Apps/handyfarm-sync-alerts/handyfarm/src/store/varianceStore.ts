import { create } from 'zustand';
import * as Crypto from 'expo-crypto';

/**
 * Local-only stand-in for the variance investigation workflow. Spec
 * section 13 describes OPEN → ACKNOWLEDGED → INVESTIGATING → RESOLVED,
 * and explicitly notes "the current backend has no acknowledgment/
 * investigation mechanism" — see BACKEND_GAPS.md. Add Note / Add
 * Evidence / Resolve are therefore left as "coming later" actions on the
 * detail screen rather than faked, since faking a resolution workflow
 * would misrepresent what's actually persisted.
 */

export type VarianceStatus = 'OPEN' | 'ACKNOWLEDGED' | 'INVESTIGATING' | 'RESOLVED';

export interface VarianceTimelineEvent {
  label: string;
  at: string; // ISO
}

export interface VarianceRecord {
  id: string;
  tankId: string;
  bookLevel: number;
  physicalLevel: number;
  varianceLitres: number;
  reason: string;
  photoUri: string | null;
  status: VarianceStatus;
  createdAt: string;
  timeline: VarianceTimelineEvent[];
}

interface VarianceStore {
  records: VarianceRecord[];
  create: (input: Omit<VarianceRecord, 'id' | 'status' | 'createdAt' | 'timeline'>) => VarianceRecord;
}

export const useVarianceStore = create<VarianceStore>((set) => ({
  records: [],
  create: (input) => {
    const now = new Date().toISOString();
    const record: VarianceRecord = {
      ...input,
      id: Crypto.randomUUID(),
      status: 'OPEN',
      createdAt: now,
      timeline: [{ label: 'Detected', at: now }],
    };
    set((s) => ({ records: [record, ...s.records] }));
    return record;
  },
}));
