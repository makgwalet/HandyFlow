import React, { useRef, useState } from "react";
import { StyleSheet, Text, View } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { CameraView, useCameraPermissions } from "expo-camera";
import * as Haptics from "expo-haptics";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { getDeviceId } from "@/auth/deviceStore";
import { getPosition } from "@/location";
import { scanItem } from "@/offline/payloads";
import { useOutbox } from "@/offline/OutboxProvider";
import { Button } from "@/ui/Button";
import { Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import type { RootStackParamList } from "./types";

type Props = NativeStackScreenProps<RootStackParamList, "Scan">;

/** The same code seen again within this long is ignored, so one held-up QR is one scan. */
const SAME_CODE_PAUSE_MS = 4000;

export function ScanScreen({ route }: Props) {
  const { shiftId } = route.params;
  const [permission, requestPermission] = useCameraPermissions();
  const { enqueue, context } = useOutbox();
  const qc = useQueryClient();
  const deviceQ = useQuery({ queryKey: ["deviceId"], queryFn: getDeviceId, staleTime: Infinity });
  const last = useRef<{ data: string; at: number } | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [count, setCount] = useState(0);

  async function onScanned(data: string) {
    const now = Date.now();
    if (last.current && last.current.data === data && now - last.current.at < SAME_CODE_PAUSE_MS) return;
    last.current = { data, at: now };
    try {
      const position = await getPosition(2000);
      await enqueue(
        scanItem(
          { qrCode: data, shiftId, position, deviceId: deviceQ.data ?? "" },
          context(),
        ),
      );
      await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      setCount((c) => c + 1);
      setMessage("Scan saved. It will show on your patrol once sent.");
      setTimeout(() => void qc.invalidateQueries({ queryKey: ["myRounds"] }), 1500);
    } catch (e) {
      setMessage(e instanceof Error ? e.message : "Could not save the scan");
    }
  }

  if (!permission) return <Screen title="Scan checkpoint"><Text style={s.muted}>Checking camera...</Text></Screen>;
  if (!permission.granted) {
    return (
      <Screen title="Scan checkpoint">
        <Text style={s.muted}>The camera is needed to scan checkpoint codes.</Text>
        <Button label="Allow camera" onPress={() => void requestPermission()} />
      </Screen>
    );
  }

  return (
    <Screen title="Scan checkpoint">
      <View style={s.frame}>
        <CameraView
          style={StyleSheet.absoluteFill}
          facing="back"
          barcodeScannerSettings={{ barcodeTypes: ["qr"] }}
          onBarcodeScanned={(r) => void onScanned(r.data)}
        />
      </View>
      <Text style={s.muted}>Point the camera at the checkpoint's QR code.</Text>
      {message ? <Text accessibilityRole="alert" style={s.ok}>{message}</Text> : null}
      {count > 0 ? <Text style={s.muted}>{count} scanned this visit.</Text> : null}
    </Screen>
  );
}

const s = StyleSheet.create({
  frame: { height: 320, borderRadius: 16, overflow: "hidden", backgroundColor: "#000" },
  muted: { color: colors.muted },
  ok: { color: colors.ok },
});
