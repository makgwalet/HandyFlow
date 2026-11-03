/**
 * Core domain types for the HandyFlow Fuel mobile foundation.
 * Kept intentionally small — these grow as each feature area (Dispense,
 * Dip, Deliveries, ...) is built out in later phases.
 */

export type Role = 'FUEL_MANAGER' | 'OPERATOR' | 'DRIVER';

export const ROLE_LABEL: Record<Role, string> = {
  FUEL_MANAGER: 'Fuel Manager',
  OPERATOR: 'Fuel Operator',
  DRIVER: 'Driver',
};

export interface AuthUser {
  id: string;
  name: string;
  role: Role;
  site: string;
  phone: string;
  initials: string;
}

export type ConnectivityStatus = 'online' | 'offline' | 'syncing' | 'error';

export interface SyncState {
  status: ConnectivityStatus;
  lastSyncedAt: string | null; // ISO timestamp
  pendingCount: number;
  failedCount: number;
}

export type TankHealth = 'healthy' | 'low' | 'critical';

export interface TankSummary {
  id: string;
  name: string;
  fuelType: 'Diesel' | 'Petrol' | 'AdBlue';
  currentLitres: number;
  capacityLitres: number;
  health: TankHealth;
}
