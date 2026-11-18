import type { ModeId } from "@/access/modes";

export type RootStackParamList = {
  Home: undefined;
  /** A mode whose screens are not built yet. */
  ModePlanned: { mode: ModeId };
  SyncCentre: undefined;
  Access: undefined;
  DriverLoads: undefined;
  LoadDetail: { loadId: string };
  CompleteLoad: { loadId: string };
  ArrivalCheck: { loadId: string };
  Signature: { loadId: string };
  FailedAttempt: { loadId: string };
  Overview: undefined;
  ReleaseCheck: { loadId: string };
  TruckStopYard: undefined;
  StayDetail: { stayId: string };
  StayRequests: undefined;
  BookStay: undefined;
  Tabs: undefined;
  TabDetail: { tabId: string };
  ForecourtShifts: undefined;
  OpenShift: undefined;
  ShiftDetail: { shiftId: string };
  CloseShift: { shiftId: string };
  Adjustment: { shiftId: string };
  DepotTanks: undefined;
  TankDetail: { tankId: string };
  ReceiveFuel: { tankId: string };
};
