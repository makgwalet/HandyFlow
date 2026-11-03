import type { Ionicons } from '@expo/vector-icons';
import type { QueueItem } from '../store/transactionQueueStore';

export const OPERATION_LABEL: Record<QueueItem['operation'], string> = {
  FUEL_DISPATCH: 'Fuel Dispensed',
  TANK_DIP: 'Tank Dip',
  DELIVERY_COMPLETE: 'Delivery Completed',
  FUEL_RECEIPT: 'Fuel Received',
  SHIFT_CLOSE: 'Shift Closed',
};

export const OPERATION_ICON: Record<QueueItem['operation'], keyof typeof Ionicons.glyphMap> = {
  FUEL_DISPATCH: 'water-outline',
  TANK_DIP: 'speedometer-outline',
  DELIVERY_COMPLETE: 'car-outline',
  FUEL_RECEIPT: 'cube-outline',
  SHIFT_CLOSE: 'time-outline',
};

/** Short one-line summary pulled from a queue item's payload for display in Sync Centre. */
export function queueItemSummary(item: QueueItem): string {
  const p = item.payload as Record<string, unknown>;
  switch (item.operation) {
    case 'FUEL_DISPATCH':
      return `${p.quantityLitres ?? '?'} L`;
    case 'TANK_DIP':
      return `Variance ${Number(p.varianceLitres ?? 0) > 0 ? '+' : ''}${p.varianceLitres ?? 0} L`;
    case 'DELIVERY_COMPLETE':
      return `${p.quantityDeliveredLitres ?? '?'} L delivered`;
    case 'FUEL_RECEIPT':
      return `${p.actualReceivedLitres ?? p.waybillQuantityLitres ?? '?'} L received`;
    case 'SHIFT_CLOSE':
      return `Variance ${Number(p.varianceLitres ?? 0) > 0 ? '+' : ''}${p.varianceLitres ?? 0} L`;
    default:
      return '';
  }
}

/** Whether a queue item carries a photo/signature payload — used for the "Photos" metric in Sync Centre. */
export function queueItemHasEvidence(item: QueueItem): boolean {
  const p = item.payload as Record<string, unknown>;
  return !!(p.photoUri || p.deliveryNotePhotoUri || p.additionalEvidenceUri);
}
