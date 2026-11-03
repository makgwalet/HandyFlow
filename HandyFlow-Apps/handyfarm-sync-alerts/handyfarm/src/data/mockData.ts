import type { TankSummary } from '../types/domain';

export const MOCK_TANKS: TankSummary[] = [
  { id: 't1', name: 'Diesel Tank 01', fuelType: 'Diesel', currentLitres: 4820, capacityLitres: 7000, health: 'healthy' },
  { id: 't2', name: 'Petrol Tank 01', fuelType: 'Petrol', currentLitres: 2520, capacityLitres: 6000, health: 'low' },
  { id: 't3', name: 'AdBlue Tank 01', fuelType: 'AdBlue', currentLitres: 1620, capacityLitres: 2000, health: 'healthy' },
];

export const MOCK_TODAY = {
  deliveries: 2,
  fuelTasks: 1,
  pendingApprovals: 2,
  variances: 1,
};

export const MOCK_VEHICLE = {
  label: 'Toyota Hilux · ABC 123 MP',
  fuelType: 'Diesel',
};
