export type RootStackParamList = {
  Home: undefined;
  Patrol: undefined;
  Scan: { shiftId: string | null };
  Incident: { siteId: string; shiftId: string | null };
  ClockOut: { sessionId: string };
};
