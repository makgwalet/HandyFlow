import * as SecureStore from "expo-secure-store";
import * as Crypto from "expo-crypto";

const DEVICE_KEY = "hf.guard.deviceId";
const SESSION_KEY = "hf.guard.session";

/**
 * Stable id for this install. The backend binds a guard to it, so it must survive
 * restarts but is not a secret. It is regenerated on reinstall, which is the point:
 * a reinstalled app is a new device and needs a supervisor code.
 */
export async function getDeviceId(): Promise<string> {
  const existing = await SecureStore.getItemAsync(DEVICE_KEY);
  if (existing) return existing;
  const fresh = Crypto.randomUUID();
  await SecureStore.setItemAsync(DEVICE_KEY, fresh);
  return fresh;
}

export async function saveSession(json: string): Promise<void> {
  await SecureStore.setItemAsync(SESSION_KEY, json, {
    keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
  });
}

export function loadSession(): Promise<string | null> {
  return SecureStore.getItemAsync(SESSION_KEY);
}

export function clearSession(): Promise<void> {
  return SecureStore.deleteItemAsync(SESSION_KEY);
}
