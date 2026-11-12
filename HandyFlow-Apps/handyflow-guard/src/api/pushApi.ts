import { api } from "./client";

/** Tells the server which phone to alert for the signed-in guard. */
export function registerPushToken(deviceHardwareId: string, expoPushToken: string): Promise<unknown> {
  return api<unknown>("/api/v1/guard/me/push-token", { method: "PUT", body: { deviceHardwareId, expoPushToken } });
}

/** On sign-out: stop alerts for this guard on this phone. */
export function unregisterPushToken(deviceHardwareId: string): Promise<unknown> {
  return api<unknown>(`/api/v1/guard/me/push-token?deviceHardwareId=${encodeURIComponent(deviceHardwareId)}`, { method: "DELETE" });
}
