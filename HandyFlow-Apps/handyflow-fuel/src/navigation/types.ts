import type { ModeId } from "@/access/modes";

export type RootStackParamList = {
  Home: undefined;
  /** A mode whose screens are not built yet. */
  ModePlanned: { mode: ModeId };
  SyncCentre: undefined;
  DriverLoads: undefined;
  LoadDetail: { loadId: string };
  CompleteLoad: { loadId: string };
  FailedAttempt: { loadId: string };
};
