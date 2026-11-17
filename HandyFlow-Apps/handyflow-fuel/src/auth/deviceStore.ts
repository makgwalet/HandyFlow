import * as SecureStore from "expo-secure-store";
import * as Crypto from "expo-crypto";

const DEVICE_KEY = "hf.fuel.deviceId";
const SESSION_KEY = "hf.fuel.session";
const TENANT_KEY = "hf.fuel.tenantSlug";

/** Stable id for this install. Not a secret. A reinstall is a new device. */
export async function getDeviceId(): Promise<string> {
  const existing = await SecureStore.getItemAsync(DEVICE_KEY);
  if (existing) return existing;
  const fresh = Crypto.randomUUID();
  await SecureStore.setItemAsync(DEVICE_KEY, fresh);
  return fresh;
}

/** The token is a secret: it goes to the keychain / keystore only, never to SQLite or logs. */
export async function saveSession(json: string): Promise<void> {
  await SecureStore.setItemAsync(SESSION_KEY, json, { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY });
}
export const loadSession = (): Promise<string | null> => SecureStore.getItemAsync(SESSION_KEY);
export const clearSession = (): Promise<void> => SecureStore.deleteItemAsync(SESSION_KEY);

/** The company code last used, so the driver types it once. */
export const saveTenantSlug = (slug: string): Promise<void> => SecureStore.setItemAsync(TENANT_KEY, slug);
export const loadTenantSlug = (): Promise<string | null> => SecureStore.getItemAsync(TENANT_KEY);
