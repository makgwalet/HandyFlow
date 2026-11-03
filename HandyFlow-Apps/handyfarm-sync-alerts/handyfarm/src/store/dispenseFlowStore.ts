import { create } from 'zustand';

/**
 * High-value threshold that gates the Authorisation (PIN) step, per spec
 * section 9's transaction-policy idea. This is a CLIENT-SIDE STOPGAP —
 * see BACKEND_GAPS.md "No transaction policy engine". A real backend
 * policy must re-validate this regardless of what the app sends, since a
 * threshold enforced only on-device isn't trustworthy.
 */
export const HIGH_RISK_LITRES = 300;

interface DispenseFlowState {
  tankId: string | null;
  vehicleId: string | null;
  quantityLitres: number;
  odometer: string;
  pumpMeterStart: number | null;
  pumpMeterEnd: string;
  pinVerified: boolean;
  photoUri: string | null;
  gps: { lat: number; lng: number; accuracy: number | null } | null;
  gpsStatus: 'idle' | 'capturing' | 'captured' | 'denied' | 'error';

  setTank: (id: string) => void;
  setVehicle: (id: string) => void;
  setQuantity: (litres: number) => void;
  setMeterReading: (odometer: string, pumpMeterStart: number, pumpMeterEnd: string) => void;
  setPinVerified: (verified: boolean) => void;
  setPhoto: (uri: string) => void;
  setGps: (
    status: DispenseFlowState['gpsStatus'],
    gps?: DispenseFlowState['gps']
  ) => void;
  reset: () => void;
}

const initialState = {
  tankId: null,
  vehicleId: null,
  quantityLitres: 0,
  odometer: '',
  pumpMeterStart: null,
  pumpMeterEnd: '',
  pinVerified: false,
  photoUri: null,
  gps: null,
  gpsStatus: 'idle' as const,
};

export const useDispenseFlowStore = create<DispenseFlowState>((set) => ({
  ...initialState,

  setTank: (id) => set({ tankId: id }),
  setVehicle: (id) => set({ vehicleId: id }),
  setQuantity: (litres) => set({ quantityLitres: litres }),
  setMeterReading: (odometer, pumpMeterStart, pumpMeterEnd) =>
    set({ odometer, pumpMeterStart, pumpMeterEnd }),
  setPinVerified: (verified) => set({ pinVerified: verified }),
  setPhoto: (uri) => set({ photoUri: uri }),
  setGps: (status, gps) => set({ gpsStatus: status, gps: gps ?? null }),
  reset: () => set(initialState),
}));
