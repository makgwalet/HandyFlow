import { Platform } from "react-native";
import * as Device from "expo-device";
import Constants from "expo-constants";
import * as Notifications from "expo-notifications";
import { registerPushToken } from "@/api/push";
import { getDeviceId } from "@/auth/deviceStore";
import type { RootStackParamList } from "@/navigation/types";

/** What an alert may open. The server names a screen and, for a load, its id; anything else is ignored. */
export type AlertTarget =
  | { screen: "DriverLoads" }
  | { screen: "LoadDetail"; params: RootStackParamList["LoadDetail"] }
  | { screen: "TankDetail"; params: RootStackParamList["TankDetail"] }
  | { screen: "ShiftDetail"; params: RootStackParamList["ShiftDetail"] }
  | { screen: "StayRequests" }
  | { screen: "Overview" };

const PLAIN = ["DriverLoads", "StayRequests", "Overview"] as const;

export function targetFromData(data: unknown): AlertTarget | null {
  const d = data as { screen?: unknown; loadId?: unknown; tankId?: unknown; shiftId?: unknown } | null | undefined;
  const id = (v: unknown): v is string => typeof v === "string" && /^[0-9a-fA-F-]{36}$/.test(v);
  if (!d || typeof d.screen !== "string") return null;
  if ((PLAIN as readonly string[]).includes(d.screen)) return { screen: d.screen } as AlertTarget;
  if (d.screen === "LoadDetail" && typeof d.loadId === "string" && /^[0-9a-fA-F-]{36}$/.test(d.loadId)) return { screen: "LoadDetail", params: { loadId: d.loadId } };
  if (d.screen === "TankDetail" && id(d.tankId)) return { screen: "TankDetail", params: { tankId: d.tankId } };
  if (d.screen === "ShiftDetail" && id(d.shiftId)) return { screen: "ShiftDetail", params: { shiftId: d.shiftId } };
  return null;
}

/** Call once at start: show alerts while the app is open, and create the Android channel the server names. Never throws. */
export async function configurePush(): Promise<void> {
  try {
    Notifications.setNotificationHandler({
      handleNotification: async () => ({ shouldShowBanner: true, shouldShowList: true, shouldPlaySound: true, shouldSetBadge: false }),
    });
    if (Platform.OS === "android") {
      await Notifications.setNotificationChannelAsync("default", { name: "Alerts", importance: Notifications.AndroidImportance.HIGH });
    }
  } catch {
    /* Expo Go and some emulators cannot create the channel; alerts are simply unavailable there */
  }
}

/**
 * Asks for permission, gets this phone's token and gives it to the server. Returns false (and does nothing else) on an
 * emulator, when the person says no, or when the build has no EAS project id yet. Never throws.
 */
export async function registerForPush(): Promise<boolean> {
  try {
    if (!Device.isDevice) return false;
    const existing = await Notifications.getPermissionsAsync();
    const granted = existing.granted || (await Notifications.requestPermissionsAsync()).granted;
    if (!granted) return false;
    const extra = Constants.expoConfig?.extra as { eas?: { projectId?: string } } | undefined;
    const projectId = extra?.eas?.projectId ?? Constants.easConfig?.projectId;
    if (!projectId) return false;
    const token = await Notifications.getExpoPushTokenAsync({ projectId });
    await registerPushToken(await getDeviceId(), token.data);
    return true;
  } catch {
    return false;
  }
}
