import { create } from 'zustand';

/**
 * Thresholds independent from the Dip flow's (see dipFlowStore.ts) —
 * receiving deliveries are typically much larger volumes, so the same
 * flat-litre approach is even more of a stopgap here. See BACKEND_GAPS.md.
 */
export const RECEIVE_VARIANCE_WARNING_LITRES = 30;
export const RECEIVE_VARIANCE_CRITICAL_LITRES = 100;

interface GpsState {
  status: 'idle' | 'capturing' | 'captured' | 'denied' | 'error';
  coords: { lat: number; lng: number; accuracy: number | null } | null;
}

interface ReceiveFlowState {
  tankId: string | null;

  supplierId: string | null;
  waybillNumber: string;
  tankerRegistration: string;
  driverName: string;

  waybillQuantityLitres: number;

  preDipLitres: string;
  postDipLitres: string;

  photoUri: string | null;
  additionalEvidenceUri: string | null;
  gps: GpsState;
  reason: string;

  setTank: (id: string) => void;
  setSupplierDetails: (input: {
    supplierId: string;
    waybillNumber: string;
    tankerRegistration: string;
    driverName: string;
  }) => void;
  setWaybillQuantity: (litres: number) => void;
  setDipReadings: (pre: string, post: string) => void;
  setPhoto: (uri: string) => void;
  setAdditionalEvidence: (uri: string) => void;
  setGps: (status: GpsState['status'], coords?: GpsState['coords']) => void;
  setReason: (reason: string) => void;
  reset: () => void;
}

const initialState = {
  tankId: null,
  supplierId: null,
  waybillNumber: '',
  tankerRegistration: '',
  driverName: '',
  waybillQuantityLitres: 0,
  preDipLitres: '',
  postDipLitres: '',
  photoUri: null,
  additionalEvidenceUri: null,
  gps: { status: 'idle' as const, coords: null },
  reason: '',
};

export const useReceiveFlowStore = create<ReceiveFlowState>((set) => ({
  ...initialState,

  setTank: (id) => set({ tankId: id }),
  setSupplierDetails: (input) => set(input),
  setWaybillQuantity: (litres) => set({ waybillQuantityLitres: litres }),
  setDipReadings: (pre, post) => set({ preDipLitres: pre, postDipLitres: post }),
  setPhoto: (uri) => set({ photoUri: uri }),
  setAdditionalEvidence: (uri) => set({ additionalEvidenceUri: uri }),
  setGps: (status, coords) => set({ gps: { status, coords: coords ?? null } }),
  setReason: (reason) => set({ reason }),
  reset: () => set(initialState),
}));
