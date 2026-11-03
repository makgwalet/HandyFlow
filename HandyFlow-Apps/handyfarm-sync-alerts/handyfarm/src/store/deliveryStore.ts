import { create } from 'zustand';
import { INITIAL_DELIVERIES } from '../data/mockDeliveries';
import type { Delivery, DeliveryProblem, GeoPoint, SignatureStroke } from '../types/delivery';

interface DeliveryStore {
  deliveries: Delivery[];
  getById: (id: string) => Delivery | undefined;

  startDelivery: (id: string) => void;
  setArrival: (id: string, gps: GeoPoint & { distanceMeters: number }, overrideReason?: string) => void;
  setMeterReadings: (id: string, meterStart: number, meterEnd: number) => void;
  setReceiver: (id: string, name: string, idNumber: string, signedOnBehalf: boolean) => void;
  setSignature: (id: string, strokes: SignatureStroke[]) => void;
  setDeliveryNotePhoto: (id: string, uri: string) => void;
  completeDelivery: (id: string) => Delivery;
  reportProblem: (id: string, problem: DeliveryProblem) => void;
}

function update(deliveries: Delivery[], id: string, patch: Partial<Delivery>): Delivery[] {
  return deliveries.map((d) => (d.id === id ? { ...d, ...patch } : d));
}

export const useDeliveryStore = create<DeliveryStore>((set, get) => ({
  deliveries: INITIAL_DELIVERIES,

  getById: (id) => get().deliveries.find((d) => d.id === id),

  startDelivery: (id) =>
    set((s) => ({
      deliveries: update(s.deliveries, id, {
        status: 'IN_TRANSIT',
        startedAt: new Date().toISOString(),
      }),
    })),

  setArrival: (id, gps, overrideReason) =>
    set((s) => ({
      deliveries: update(s.deliveries, id, {
        status: 'ARRIVED',
        arrivalGps: gps,
        arrivalOverrideReason: overrideReason,
      }),
    })),

  setMeterReadings: (id, meterStart, meterEnd) =>
    set((s) => ({
      deliveries: update(s.deliveries, id, {
        meterStart,
        meterEnd,
        quantityDeliveredLitres: Math.max(0, meterEnd - meterStart),
      }),
    })),

  setReceiver: (id, name, idNumber, signedOnBehalf) =>
    set((s) => ({
      deliveries: update(s.deliveries, id, {
        receiverName: name,
        receiverIdNumber: idNumber,
        signedOnBehalf,
      }),
    })),

  setSignature: (id, strokes) =>
    set((s) => ({ deliveries: update(s.deliveries, id, { signatureStrokes: strokes }) })),

  setDeliveryNotePhoto: (id, uri) =>
    set((s) => ({ deliveries: update(s.deliveries, id, { deliveryNotePhotoUri: uri }) })),

  completeDelivery: (id) => {
    const receiptNumber = `FDR-2026-${Math.floor(1000 + Math.random() * 9000)}`;
    set((s) => ({
      deliveries: update(s.deliveries, id, {
        status: 'COMPLETED',
        completedAt: new Date().toISOString(),
        receiptNumber,
      }),
    }));
    return get().getById(id)!;
  },

  reportProblem: (id, problem) =>
    set((s) => ({
      deliveries: update(s.deliveries, id, { status: 'PROBLEM', problem }),
    })),
}));
