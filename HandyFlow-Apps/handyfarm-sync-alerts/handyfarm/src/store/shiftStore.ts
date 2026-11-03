import { create } from 'zustand';
import * as Crypto from 'expo-crypto';
import type { Shift } from '../types/shift';

interface ShiftStore {
  activeShift: Shift | null;
  closedShifts: Shift[];
  startShift: (tankId: string, openingLevel: number, operatorId: string, operatorName: string) => Shift;
  closeShift: (physicalReading: number, varianceLitres: number, explanation?: string) => Shift;
}

export const useShiftStore = create<ShiftStore>((set, get) => ({
  activeShift: null,
  closedShifts: [],

  startShift: (tankId, openingLevel, operatorId, operatorName) => {
    const shift: Shift = {
      id: Crypto.randomUUID(),
      operatorId,
      operatorName,
      tankId,
      status: 'OPEN',
      startedAt: new Date().toISOString(),
      openingLevel,
    };
    set({ activeShift: shift });
    return shift;
  },

  closeShift: (physicalReading, varianceLitres, explanation) => {
    const active = get().activeShift;
    if (!active) throw new Error('No active shift to close');
    const closed: Shift = {
      ...active,
      status: 'CLOSED',
      closedAt: new Date().toISOString(),
      physicalReading,
      varianceLitres,
      explanation,
    };
    set((s) => ({ activeShift: null, closedShifts: [closed, ...s.closedShifts] }));
    return closed;
  },
}));
