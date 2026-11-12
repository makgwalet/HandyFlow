import * as FileSystem from "expo-file-system/legacy";
import { z } from "zod";
import { myPostSchema, orderSchema, type MyPost, type PostOrder } from "@/api/guardWork";

/**
 * The last post orders this guard loaded, kept in the app's private storage so they can be read (and acknowledged)
 * with no signal. One file per guard, so on a shared phone nobody reads another guard's copy.
 */
const DIR = `${FileSystem.documentDirectory ?? ""}guard-cache/`;

const cacheSchema = z.object({
  savedAt: z.string(),
  myPost: myPostSchema.nullable(),
  orders: z.record(z.string(), orderSchema),
});
export type PostOrderCache = z.infer<typeof cacheSchema>;

const fileFor = (guardId: string) => `${DIR}post-orders-${guardId.replace(/[^a-zA-Z0-9-]/g, "")}.json`;

export async function loadCache(guardId: string): Promise<PostOrderCache | null> {
  try {
    const parsed = cacheSchema.safeParse(JSON.parse(await FileSystem.readAsStringAsync(fileFor(guardId))));
    return parsed.success ? parsed.data : null;
  } catch {
    return null;
  }
}

async function write(guardId: string, cache: PostOrderCache): Promise<void> {
  try {
    await FileSystem.makeDirectoryAsync(DIR, { intermediates: true });
    await FileSystem.writeAsStringAsync(fileFor(guardId), JSON.stringify(cache));
  } catch {
    // A cache that cannot be written only means no offline copy; never break the screen over it.
  }
}

export async function saveMyPost(guardId: string, myPost: MyPost): Promise<void> {
  const old = await loadCache(guardId);
  await write(guardId, { savedAt: new Date().toISOString(), myPost, orders: old?.orders ?? {} });
}

export async function saveOrder(guardId: string, postId: string, order: PostOrder): Promise<void> {
  const old = await loadCache(guardId);
  await write(guardId, { savedAt: new Date().toISOString(), myPost: old?.myPost ?? null, orders: { ...(old?.orders ?? {}), [postId]: order } });
}

/** Removes this guard's saved copy (sign-out). */
export async function clearCache(guardId: string): Promise<void> {
  try {
    await FileSystem.deleteAsync(fileFor(guardId), { idempotent: true });
  } catch {
    // nothing to do
  }
}
