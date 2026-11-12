import React, { useState } from "react";
import { Image, StyleSheet, Text, TextInput, View } from "react-native";
import type { NativeStackScreenProps } from "@react-navigation/native-stack";
import { getPosition } from "@/location";
import { evidenceItem, incidentItem, INCIDENT_TYPES, SEVERITIES, type IncidentType, type Severity } from "@/offline/payloads";
import { useOutbox } from "@/offline/OutboxProvider";
import { MAX_PHOTOS, removePhoto, takePhoto } from "@/photos";
import { Button } from "@/ui/Button";
import { Screen } from "@/ui/Screen";
import { colors } from "@/ui/theme";
import type { RootStackParamList } from "./types";

type Props = NativeStackScreenProps<RootStackParamList, "Incident">;

function Choice<T extends string>({ label, options, value, onChange }: { label: string; options: readonly T[]; value: T; onChange: (v: T) => void }) {
  return (
    <View style={s.group}>
      <Text style={s.label}>{label}</Text>
      <View style={s.row}>
        {options.map((o) => (
          <Button key={o} label={o.charAt(0) + o.slice(1).toLowerCase()} tone={o === value ? "primary" : "plain"} onPress={() => onChange(o)} />
        ))}
      </View>
    </View>
  );
}

export function IncidentScreen({ route, navigation }: Props) {
  const { siteId, shiftId } = route.params;
  const { enqueue, context } = useOutbox();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [severity, setSeverity] = useState<Severity>("MEDIUM");
  const [type, setType] = useState<IncidentType>("OTHER");
  const [photos, setPhotos] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function addPhoto() {
    setError(null);
    try {
      const uri = await takePhoto();
      if (uri) setPhotos((p) => [...p, uri]);
      else setError("No photo added. Allow the camera in Settings if you want to attach one.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not take the photo");
    }
  }

  function dropPhoto(uri: string) {
    setPhotos((p) => p.filter((x) => x !== uri));
    void removePhoto(uri);
  }

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      const position = await getPosition(2500);
      const ctx = context();
      const incident = incidentItem({ siteId, shiftId, title, description, severity, type, position }, ctx);
      await enqueue(incident);
      // Photos are queued behind the report; each names the report by its id, so order of arrival does not matter.
      for (const localUri of photos) {
        await enqueue(evidenceItem({ incidentRef: incident.id, localUri, label: title.trim() }, ctx));
      }
      navigation.goBack();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not save the report");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Screen title="Report an incident">
      <Text style={s.label}>What happened (short title)</Text>
      <TextInput style={s.input} value={title} onChangeText={setTitle} maxLength={120} accessibilityLabel="Title" />
      <Text style={s.label}>Details</Text>
      <TextInput style={[s.input, s.multi]} value={description} onChangeText={setDescription} multiline accessibilityLabel="Details" />
      <Choice label="How serious" options={SEVERITIES} value={severity} onChange={setSeverity} />
      <Choice label="Kind" options={INCIDENT_TYPES} value={type} onChange={setType} />
      <View style={s.group}>
        <Text style={s.label}>Photos ({photos.length}/{MAX_PHOTOS})</Text>
        <View style={s.thumbs}>
          {photos.map((uri) => (
            <View key={uri} style={s.thumb}>
              <Image source={{ uri }} style={s.img} accessibilityLabel="Photo attached to this report" />
              <Button label="Remove" tone="plain" onPress={() => dropPhoto(uri)} />
            </View>
          ))}
        </View>
        {photos.length < MAX_PHOTOS ? <Button label="Take a photo" tone="plain" onPress={() => void addPhoto()} /> : null}
      </View>
      {error ? <Text accessibilityRole="alert" style={s.err}>{error}</Text> : null}
      <Button label="Save report" onPress={() => void submit()} loading={busy} disabled={title.trim().length < 3} />
    </Screen>
  );
}

const s = StyleSheet.create({
  group: { gap: 6 },
  thumbs: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  thumb: { width: 120, gap: 4 },
  img: { width: 120, height: 120, borderRadius: 8, backgroundColor: colors.surface },
  row: { gap: 8 },
  label: { color: colors.muted, fontSize: 14 },
  muted: { color: colors.muted },
  err: { color: colors.danger },
  input: { backgroundColor: colors.surface, color: colors.text, borderColor: colors.border, borderWidth: 1, borderRadius: 12, minHeight: 52, paddingHorizontal: 14, fontSize: 17 },
  multi: { minHeight: 110, textAlignVertical: "top", paddingTop: 12 },
});
