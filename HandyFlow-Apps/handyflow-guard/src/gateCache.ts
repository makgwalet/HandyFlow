import * as FileSystem from "expo-file-system/legacy";
import { z } from "zod";
import { accessPointSchema, gateEntrySchema, type AccessPoint, type GateEntry } from "@/api/guardWork";

/**
 * The gates and the "on site now" list as last loaded, kept in the app's private storage so a guard with no signal can
 * still pick a gate and see who was on site. One file per guard. The list can be out of date; the screen says when it
 * was saved.
 */
const DIR = `${FileSystem.documentDirectory ?? ""}guard-cache/`;

const schema = z.object({
  savedAt: z.string(),
  accessPoints: z.array(accessPointSchema).nullable(),
  onSite: z.array(gateEntrySchema).nullable(),
});
type GateCache = z.infer<typeof schema>;

const fileFor = (guardId: string) => `${DIR}gate-${guardId.replace(/[^a-zA-Z0-9-]/g, "")}.json`;

export async function loadGateCache(guardId: string): Promise<GateCache | null> {
  try {
    const parsed = schema.safeParse(JSON.parse(await FileSystem.readAsStringAsync(fileFor(guardId))));
    return parsed.success ? parsed.data : null;
  } catch {
    return null;
  }
}

async function save(guardId: string, patch: Partial<Pick<GateCache, "accessPoints" | "onSite">>): Promise<void> {
  try {
    const old = await loadGateCache(guardId);
    await FileSystem.makeDirectoryAsync(DIR, { intermediates: true });
    const next: GateCache = {
      savedAt: new Date().toISOString(),
      accessPoints: patch.accessPoints ?? old?.accessPoints ?? null,
      onSite: patch.onSite ?? old?.onSite ?? null,
    };
    await FileSystem.writeAsStringAsync(fileFor(guardId), JSON.stringify(next));
  } catch {
    // No offline copy is better than a broken screen.
  }
}

export const saveAccessPoints = (guardId: string, accessPoints: AccessPoint[]) => save(guardId, { accessPoints });
export const saveOnSite = (guardId: string, onSite: GateEntry[]) => save(guardId, { onSite });

export async function clearGateCache(guardId: string): Promise<void> {
  try {
    await FileSystem.deleteAsync(fileFor(guardId), { idempotent: true });
  } catch {
    // nothing to do
  }
}
