import type { Delivery } from '../types/delivery';

const DEPOT_A = { name: 'Depot A', lat: -26.214, lng: 28.042 };

export const INITIAL_DELIVERIES: Delivery[] = [
  {
    id: 'd1',
    reference: 'FD-2026-00231',
    customerName: 'ABC Mining',
    siteName: 'Site 4',
    quantityLitres: 8000,
    fuelType: 'Diesel',
    distanceKm: 42,
    scheduledTime: '10:00',
    pickup: DEPOT_A,
    destination: { name: 'ABC Mining — Site 4', lat: -26.2041, lng: 28.0473 },
    status: 'SCHEDULED',
  },
  {
    id: 'd2',
    reference: 'FD-2026-00232',
    customerName: 'Sunrise Farm',
    siteName: '',
    quantityLitres: 9800,
    fuelType: 'Diesel',
    distanceKm: 26,
    scheduledTime: '11:00',
    pickup: DEPOT_A,
    destination: { name: 'Sunrise Farm', lat: -26.152, lng: 27.985 },
    status: 'SCHEDULED',
  },
  {
    id: 'd3',
    reference: 'FD-2026-00233',
    customerName: 'Agri Solutions',
    siteName: '',
    quantityLitres: 2000,
    fuelType: 'Petrol',
    distanceKm: 20,
    scheduledTime: '14:00',
    pickup: DEPOT_A,
    destination: { name: 'Agri Solutions', lat: -26.0936, lng: 28.0567 },
    status: 'SCHEDULED',
  },
];
