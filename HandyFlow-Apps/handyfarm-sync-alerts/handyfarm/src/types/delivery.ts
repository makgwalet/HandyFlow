export type DeliveryStatus = 'SCHEDULED' | 'IN_TRANSIT' | 'ARRIVED' | 'COMPLETED' | 'PROBLEM';

export interface GeoPoint {
  lat: number;
  lng: number;
}

export type SignatureStroke = { x: number; y: number }[];

export interface DeliveryProblem {
  reason: string;
  notes: string;
  photoUri: string | null;
  gps: GeoPoint | null;
  reportedAt: string;
}

export interface Delivery {
  id: string;
  reference: string;
  customerName: string;
  siteName: string;
  quantityLitres: number;
  fuelType: 'Diesel' | 'Petrol' | 'AdBlue';
  distanceKm: number;
  scheduledTime: string;
  pickup: { name: string } & GeoPoint;
  destination: { name: string } & GeoPoint;
  status: DeliveryStatus;

  startedAt?: string;
  arrivalGps?: GeoPoint & { distanceMeters: number };
  arrivalOverrideReason?: string;

  meterStart?: number;
  meterEnd?: number;
  quantityDeliveredLitres?: number;

  receiverName?: string;
  receiverIdNumber?: string;
  signedOnBehalf?: boolean;
  signatureStrokes?: SignatureStroke[];
  deliveryNotePhotoUri?: string;

  problem?: DeliveryProblem;

  completedAt?: string;
  receiptNumber?: string;
}
