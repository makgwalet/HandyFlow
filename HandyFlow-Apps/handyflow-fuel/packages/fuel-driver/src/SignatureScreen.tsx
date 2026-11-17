import React, { useRef, useState } from "react";
import { PanResponder, StyleSheet, Text, View, type LayoutChangeEvent } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { keepBase64 } from "@/files";
import type { RootStackParamList } from "@/navigation/types";
import { useOutbox } from "@/offline/OutboxProvider";
import { Button } from "@/ui/Button";
import { Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import { blankBitmap, drawStrokes, hasSignature, signatureBase64, type Stroke } from "./png";

type Props = NativeStackScreenProps<RootStackParamList, "Signature">;
const MIN_STEP = 3;

export function SignatureScreen({ route, navigation }: Props) {
  const { loadId } = route.params;
  const { enqueue } = useOutbox();
  const [strokes, setStrokes] = useState<Stroke[]>([]);
  const [size, setSize] = useState({ w: 0, h: 0 });
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const current = useRef<Stroke | null>(null);

  const pan = useRef(PanResponder.create({
    onStartShouldSetPanResponder: () => true,
    onMoveShouldSetPanResponder: () => true,
    onPanResponderGrant: (e) => {
      current.current = [{ x: e.nativeEvent.locationX, y: e.nativeEvent.locationY }];
      setStrokes((s) => [...s, current.current!]);
    },
    onPanResponderMove: (e) => {
      const s = current.current;
      if (!s) return;
      const last = s[s.length - 1]!;
      const p = { x: e.nativeEvent.locationX, y: e.nativeEvent.locationY };
      if (Math.hypot(p.x - last.x, p.y - last.y) < MIN_STEP) return;
      s.push(p);
      setStrokes((all) => [...all.slice(0, -1), [...s]]);
    },
    onPanResponderRelease: () => { current.current = null; },
  })).current;

  const onLayout = (e: LayoutChangeEvent) => setSize({ w: e.nativeEvent.layout.width, h: e.nativeEvent.layout.height });

  async function save() {
    const bitmap = drawStrokes(strokes, size.w, size.h, blankBitmap());
    if (!hasSignature(bitmap)) { setError("Ask the receiver to sign in the box."); return; }
    setError(null); setBusy(true);
    try {
      const uri = await keepBase64(signatureBase64(bitmap), "signature.png");
      await enqueue("EVIDENCE", { loadId, kind: "SIGNATURE", uri, name: "signature.png", mimeType: "image/png", note: "Receiver signature", latitude: null, longitude: null, accuracyM: null });
      navigation.popTo("LoadDetail", { loadId });
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save the signature");
    } finally { setBusy(false); }
  }

  return (
    <Screen title="Receiver's signature">
      <Text style={{ color: colors.muted, fontSize: 15 }}>The person who received the fuel signs below with a finger.</Text>
      <View style={styles.pad} onLayout={onLayout} {...pan.panHandlers} accessibilityLabel="Signature box">
        {strokes.flatMap((s, i) => s.map((p, j) => <View key={`${i}-${j}`} pointerEvents="none" style={[styles.dot, { left: p.x - 2, top: p.y - 2 }]} />))}
      </View>
      {error ? <Text accessibilityRole="alert" style={{ color: "#FCA5A5", fontSize: 15 }}>{error}</Text> : null}
      <Button label="Clear" tone="plain" onPress={() => { setStrokes([]); setError(null); }} />
      <Button label="Save signature" onPress={save} loading={busy} />
    </Screen>
  );
}

const styles = StyleSheet.create({
  pad: { height: 200, borderRadius: 12, borderWidth: 1, borderColor: colors.border, backgroundColor: "#FFFFFF", overflow: "hidden" },
  dot: { position: "absolute", width: 5, height: 5, borderRadius: 3, backgroundColor: "#000000" },
});
