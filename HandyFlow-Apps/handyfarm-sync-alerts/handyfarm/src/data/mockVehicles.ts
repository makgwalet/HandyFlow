export interface MockVehicle {
  id: string;
  registration: string;
  label: string;
  fuelType: 'Diesel' | 'Petrol';
  lastOdometer: number;
  /**
   * Per-vehicle fill cap shown as "Maximum allowed" in the Dispense flow.
   * This is NOT authoritative — see BACKEND_GAPS.md: nothing server-side
   * enforces this today, so it's a UX guardrail only until a real
   * transaction-policy endpoint exists.
   */
  maxAllowedLitres: number;
}

export const MOCK_VEHICLES: MockVehicle[] = [
  {
    id: 'v1',
    registration: 'ABC 123 MP',
    label: 'Toyota Hilux',
    fuelType: 'Diesel',
    lastOdometer: 245821,
    maxAllowedLitres: 500,
  },
  {
    id: 'v2',
    registration: 'BDF 456 GP',
    label: 'Isuzu Truck',
    fuelType: 'Diesel',
    lastOdometer: 118342,
    maxAllowedLitres: 1200,
  },
  {
    id: 'v3',
    registration: 'JKL 789 FS',
    label: 'Ford Ranger',
    fuelType: 'Diesel',
    lastOdometer: 88210,
    maxAllowedLitres: 400,
  },
  {
    id: 'v4',
    registration: 'TRK-102',
    label: 'Tanker',
    fuelType: 'Diesel',
    lastOdometer: 302940,
    maxAllowedLitres: 5000,
  },
];

// Current pump-meter reading per tank, so the Meter Reading step has a
// "current reading" to show. Keyed by MOCK_TANKS id from mockData.ts.
export const PUMP_METER_CURRENT: Record<string, number> = {
  t1: 12442.1,
  t2: 8021.4,
  t3: 3310.0,
};
