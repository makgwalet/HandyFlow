import * as Location from "expo-location";
import type { Position } from "@/offline/payloads";

/**
 * Where the phone is, quickly. Never blocks an action: after `timeoutMs` it falls back to the last known position, then to
 * null. A scan, a report or a panic press goes out with or without a fix.
 */
export async function getPosition(timeoutMs = 4000): Promise<Position | null> {
  try {
    let perm = await Location.getForegroundPermissionsAsync();
    if (!perm.granted && perm.canAskAgain) perm = await Location.requestForegroundPermissionsAsync();
    if (!perm.granted) return null;

    const fix = await Promise.race([
      Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.Balanced }),
      new Promise<null>((resolve) => setTimeout(() => resolve(null), timeoutMs)),
    ]);
    const use = fix ?? (await Location.getLastKnownPositionAsync());
    if (!use) return null;
    return { latitude: use.coords.latitude, longitude: use.coords.longitude, accuracy: use.coords.accuracy ?? null };
  } catch {
    return null;
  }
}
