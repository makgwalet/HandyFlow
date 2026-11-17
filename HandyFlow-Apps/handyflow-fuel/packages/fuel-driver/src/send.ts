import { api } from "@/api/client";
import { removeFile } from "@/files";
import type { OutboxItem } from "@/offline/outbox";

const base = (loadId: unknown) => `/api/v1/fuel/driver/loads/${String(loadId)}`;

/**
 * Sends one queued item. `item.id` goes as clientRequestId, so the server returns the first result if a reply was lost
 * and the same item is sent again. Throws ApiError, which the outbox sorts into retry, park or stop.
 */
export async function sendDriverItem(item: OutboxItem): Promise<void> {
  const p = item.payload;
  switch (item.kind) {
    case "DISPATCH":
      await api(`${base(p.loadId)}/dispatch`, { method: "POST" });
      return;
    case "COMPLETE":
      await api(`${base(p.loadId)}/complete`, { method: "POST", body: { ...(p.body as object), clientRequestId: item.id } });
      return;
    case "FAILED_ATTEMPT":
      await api(`${base(p.loadId)}/failed-attempt`, { method: "POST", body: { ...(p.body as object), clientRequestId: item.id } });
      return;
    case "EVIDENCE":
      await sendEvidence(item);
      return;
  }
}

async function sendEvidence(item: OutboxItem): Promise<void> {
  const p = item.payload;
  const form = new FormData();
  // React Native's FormData takes { uri, name, type } for a file on the phone.
  form.append("file", { uri: String(p.uri), name: String(p.name ?? "evidence.jpg"), type: String(p.mimeType ?? "image/jpeg") } as unknown as Blob);
  form.append("kind", String(p.kind ?? "PHOTO"));
  if (p.note) form.append("note", String(p.note));
  form.append("clientRequestId", item.id);
  await api(`${base(p.loadId)}/evidence/file`, { method: "POST", form });
  // The server has it. Only now is the phone's copy no longer needed.
  await removeFile(String(p.uri));
}
