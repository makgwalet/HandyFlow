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
  DepotTanks: undefined;
  TankDetail: { tankId: string };
  ReceiveFuel: { tankId: string };
};
