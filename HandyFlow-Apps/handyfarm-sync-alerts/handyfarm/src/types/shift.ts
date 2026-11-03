export type ShiftStatus = 'OPEN' | 'CLOSED';

export interface Shift {
  id: string;
  operatorId: string;
  operatorName: string;
  tankId: string;
  status: ShiftStatus;
  startedAt: string;
  openingLevel: number;
  closedAt?: string;
  physicalReading?: number;
  varianceLitres?: number;
  explanation?: string;
}
