import { create } from 'zustand';

/**
 * Variance thresholds — client-side stopgap. See BACKEND_GAPS.md: there is
 * no variance-policy resource server-side, so these numbers can't be tuned
 * per tenant/tank/fuel-type without an app release. Matches the mockup's
 * "-260 L ⚠ CRITICAL" framing at a 20,000 L tank scale.
 */
export const VARIANCE_WARNING_LITRES = 50;
export const VARIANCE_CRITICAL_LITRES = 150;

export type VarianceSeverity = 'none' | 'warning' | 'critical';

export function varianceSeverity(
  varianceLitres: number,
  warningThreshold: number = VARIANCE_WARNING_LITRES,
  criticalThreshold: number = VARIANCE_CRITICAL_LITRES
): VarianceSeverity {
  const abs = Math.abs(varianceLitres);
  if (abs >= criticalThreshold) return 'critical';
  if (abs >= warningThreshold) return 'warning';
  return 'none';
}

interface GpsState {
  status: 'idle' | 'capturing' | 'captured' | 'denied' | 'error';
  coords: { lat: number; lng: number; accuracy: number | null } | null;
}

interface DipFlowState {
  tankId: string | null;
  physicalDipLitres: number;
  photoUri: string | null;
  gps: GpsState;
  reason: string;
  additionalEvidenceUri: string | null;

  setTank: (id: string) => void;
  setPhysicalDip: (litres: number) => void;
  setPhoto: (uri: string) => void;
  setAdditionalEvidence: (uri: string) => void;
  setGps: (status: GpsState['status'], coords?: GpsState['coords']) => void;
  setReason: (reason: string) => void;
  reset: () => void;
}

const initialState = {
  tankId: null,
  physicalDipLitres: 0,
  photoUri: null,
  gps: { status: 'idle' as const, coords: null },
  reason: '',
  additionalEvidenceUri: null,
};

export const useDipFlowStore = create<DipFlowState>((set) => ({
  ...initialState,

  setTank: (id) => set({ tankId: id }),
  setPhysicalDip: (litres) => set({ physicalDipLitres: litres }),
  setPhoto: (uri) => set({ photoUri: uri }),
  setAdditionalEvidence: (uri) => set({ additionalEvidenceUri: uri }),
  setGps: (status, coords) => set({ gps: { status, coords: coords ?? null } }),
  setReason: (reason) => set({ reason }),
  reset: () => set(initialState),
}));
