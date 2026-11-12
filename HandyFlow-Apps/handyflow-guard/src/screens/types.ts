export type RootStackParamList = {
  Home: undefined;
  Patrol: undefined;
  MyPost: undefined;
  Swaps: undefined;
  Equipment: { sessionId: string };
  Scan: { shiftId: string | null };
  Incident: { siteId: string; shiftId: string | null };
  ClockOut: { sessionId: string };
};
