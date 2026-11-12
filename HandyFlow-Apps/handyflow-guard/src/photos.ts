import * as ImagePicker from "expo-image-picker";
import * as FileSystem from "expo-file-system/legacy";

/** Photos wait here, inside the app's private storage, until the queue has uploaded them. */
const DIR = `${FileSystem.documentDirectory ?? ""}guard-photos/`;

/** Most photos one report can carry from the phone. The server allows 20 per incident in total. */
export const MAX_PHOTOS = 5;

/**
 * Opens the camera and keeps the picture. Returns null when the guard cancels or refuses camera access.
 * Quality is lowered so a photo is small enough to upload on a weak signal (the server accepts up to 5 MB).
 */
export async function takePhoto(): Promise<string | null> {
  const permission = await ImagePicker.requestCameraPermissionsAsync();
  if (!permission.granted) return null;
  const result = await ImagePicker.launchCameraAsync({ mediaTypes: ["images"], quality: 0.6 });
  const asset = result.canceled ? undefined : result.assets[0];
  if (!asset) return null;
  await FileSystem.makeDirectoryAsync(DIR, { intermediates: true });
  const kept = `${DIR}${Date.now()}-${Math.random().toString(36).slice(2, 8)}.jpg`;
  await FileSystem.copyAsync({ from: asset.uri, to: kept });
  return kept;
}

/** Deletes a kept photo. Missing files are fine. */
export async function removePhoto(uri: string): Promise<void> {
  try {
    await FileSystem.deleteAsync(uri, { idempotent: true });
  } catch {
    // Nothing useful to do: the upload either already worked or the file is gone.
  }
}
