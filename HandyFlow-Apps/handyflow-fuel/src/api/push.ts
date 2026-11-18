import { api } from "./client";

/** Tells the server which phone to alert for the signed-in user. */
export function registerPushToken(deviceId: string, expoPushToken: string): Promise<unknown> {
  return api<unknown>("/api/v1/fuel/me/push-token", { method: "PUT", body: { deviceId, expoPushToken } });
}

/** On sign-out: stop alerts for this user on this phone. */
export function unregisterPushToken(deviceId: string): Promise<unknown> {
  return api<unknown>(`/api/v1/fuel/me/push-token?deviceId=${encodeURIComponent(deviceId)}`, { method: "DELETE" });
}
