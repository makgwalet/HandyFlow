import * as FileSystem from "expo-file-system/legacy";

/**
 * A photo from the camera lives in a cache folder the phone may empty at any time. A queued upload can wait hours, so
 * it is copied to the app's own folder first and deleted once the server has it.
 */
const DIR = `${FileSystem.documentDirectory ?? ""}fuel-evidence/`;

export async function keepFile(sourceUri: string, name: string): Promise<string> {
  await FileSystem.makeDirectoryAsync(DIR, { intermediates: true });
  const kept = `${DIR}${Date.now()}-${name.replace(/[^A-Za-z0-9._-]/g, "_")}`;
  await FileSystem.copyAsync({ from: sourceUri, to: kept });
  return kept;
}

/** Writes bytes already encoded as base64 (a signature PNG) to the app's own folder. */
export async function keepBase64(base64: string, name: string): Promise<string> {
  await FileSystem.makeDirectoryAsync(DIR, { intermediates: true });
  const kept = `${DIR}${Date.now()}-${name.replace(/[^A-Za-z0-9._-]/g, "_")}`;
  await FileSystem.writeAsStringAsync(kept, base64, { encoding: FileSystem.EncodingType.Base64 });
  return kept;
}

export async function removeFile(uri: string): Promise<void> {
  try { await FileSystem.deleteAsync(uri, { idempotent: true }); } catch { /* an orphan file is harmless */ }
}
