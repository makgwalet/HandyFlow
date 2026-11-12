import { Platform } from "react-native";
import * as Device from "expo-device";
import Constants from "expo-constants";
import * as Notifications from "expo-notifications";
import { getDeviceId } from "@/auth/deviceStore";
import { registerPushToken } from "@/api/pushApi";
import type { RootStackParamList } from "@/screens/types";

/** Screens an alert may open. The server only ever names one of these. */
const OPENABLE = ["Home", "Swaps", "Patrol", "MyPost", "Gate", "Supervisor"] as const;
export type OpenableScreen = (typeof OPENABLE)[number] & keyof RootStackParamList;

export function screenFromData(data: unknown): OpenableScreen | null {
  const screen = (data as { screen?: unknown } | null | undefined)?.screen;
  return typeof screen === "string" && (OPENABLE as readonly string[]).includes(screen) ? (screen as OpenableScreen) : null;
}

/** Call once at start: show alerts while the app is open, and create the Android channel the server names. Never throws. */
export async function configurePush(): Promise<void> {
  try {
    Notifications.setNotificationHandler({
      handleNotification: async () => ({ shouldShowBanner: true, shouldShowList: true, shouldPlaySound: true, shouldSetBadge: false }),
    });
    if (Platform.OS === "android") {
      await Notifications.setNotificationChannelAsync("default", {
        name: "Alerts",
        importance: Notifications.AndroidImportance.HIGH,
      });
    }
  } catch {
    /* Expo Go and some emulators cannot create the channel; alerts are simply unavailable there */
  }
}

/**
 * Asks for permission, gets this phone's token and gives it to the server. Returns false (and does nothing else) on an
 * emulator, when the guard says no, or when the build has no EAS project id yet. Never throws.
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
