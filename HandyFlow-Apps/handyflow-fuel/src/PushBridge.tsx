import { useEffect } from "react";
import * as Notifications from "expo-notifications";
import { createNavigationContainerRef } from "@react-navigation/native";
import { useAuth } from "@/auth/AuthContext";
import type { RootStackParamList } from "@/navigation/types";
import { configurePush, registerForPush, targetFromData, type AlertTarget } from "@/push";

export const navigationRef = createNavigationContainerRef<RootStackParamList>();

function open(t: AlertTarget | null) {
  if (!t || !navigationRef.isReady()) return;
  if (t.screen === "LoadDetail") navigationRef.navigate("LoadDetail", t.params);
  else if (t.screen === "TankDetail") navigationRef.navigate("TankDetail", t.params);
  else if (t.screen === "ShiftDetail") navigationRef.navigate("ShiftDetail", t.params);
  else navigationRef.navigate(t.screen);
}

void configurePush();

/** Renders nothing. While someone is signed in it registers this phone for alerts and opens the screen an alert names. */
export function PushBridge() {
  const { session } = useAuth();
  const userId = session?.userId;
  useEffect(() => {
    if (userId) void registerForPush();
  }, [userId]);
  useEffect(() => {
    const sub = Notifications.addNotificationResponseReceivedListener((r) => open(targetFromData(r.notification.request.content.data)));
    // The app was closed when the alert was tapped.
    void Notifications.getLastNotificationResponseAsync().then((r) => { if (r) open(targetFromData(r.notification.request.content.data)); }).catch(() => undefined);
    return () => sub.remove();
  }, []);
  return null;
}
