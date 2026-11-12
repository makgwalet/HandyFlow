// Photo and signature capture. A phone photo is shrunk before it is kept or sent: proof of delivery needs a legible
// picture, not a 12 MP original, and a small file is what lets an offline queue hold several of them.
export interface QueueFile { dataUrl: string; name: string }

export const MAX_EDGE = 1280
export const JPEG_QUALITY = 0.7
export const MAX_UPLOAD_BYTES = 5 * 1024 * 1024

/** The size to draw an image at so its longest side is at most `max`, never enlarging it. */
export function fitWithin(width: number, height: number, max = MAX_EDGE): { width: number; height: number } {
  const longest = Math.max(width, height)
  if (longest <= max || longest <= 0) return { width: Math.max(1, Math.round(width)), height: Math.max(1, Math.round(height)) }
  const k = max / longest
  return { width: Math.max(1, Math.round(width * k)), height: Math.max(1, Math.round(height * k)) }
}

/** Reads a camera or gallery image and returns it as a smaller JPEG. Rejects when the file is not an image the browser can read. */
export async function shrinkImage(file: Blob, name = "photo.jpg", max = MAX_EDGE, quality = JPEG_QUALITY): Promise<QueueFile> {
  const bitmap = await createImageBitmap(file)
  try {
    const { width, height } = fitWithin(bitmap.width, bitmap.height, max)
    const canvas = document.createElement("canvas"); canvas.width = width; canvas.height = height
    const ctx = canvas.getContext("2d")
    if (!ctx) throw new Error("This browser cannot prepare the photo.")
    ctx.fillStyle = "#fff"; ctx.fillRect(0, 0, width, height)
    ctx.drawImage(bitmap, 0, 0, width, height)
    return { dataUrl: canvas.toDataURL("image/jpeg", quality), name: name.replace(/\.[^.]+$/, "") + ".jpg" }
  } finally { bitmap.close?.() }
}

export function dataUrlToBlob(dataUrl: string): Blob {
  const [head, data = ""] = dataUrl.split(",")
  const type = /data:([^;]+)/.exec(head)?.[1] ?? "application/octet-stream"
  const bin = atob(data); const bytes = new Uint8Array(bin.length)
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
  return new Blob([bytes], { type })
}

/** The multipart body for an evidence upload: the plain fields, then the file. */
export function uploadForm(body: Record<string, unknown> | null, file: QueueFile): FormData {
  const f = new FormData()
  for (const [k, v] of Object.entries(body ?? {})) if (v != null && v !== "") f.append(k, String(v))
  f.append("file", dataUrlToBlob(file.dataUrl), file.name)
  return f
}

export const UPLOAD_KINDS: { code: "PHOTO" | "SIGNATURE" | "DOCUMENT"; label: string }[] = [
  { code: "PHOTO", label: "Photo" }, { code: "SIGNATURE", label: "Signature" }, { code: "DOCUMENT", label: "Document" },
]

/** A check before sending; the server decides again from the file's real contents. Null when the file is fine to send. */
export function validateUpload(kind: string, file: { type: string; size: number } | null): string | null {
  if (!file) return "Choose a file."
  if (file.size <= 0) return "That file is empty."
  if (file.size > MAX_UPLOAD_BYTES) return "That file is too large. The limit is 5 MB."
  const image = ["image/jpeg", "image/png", "image/webp"].includes(file.type)
  if (!image && !(kind === "DOCUMENT" && file.type === "application/pdf")) return kind === "DOCUMENT" ? "Attach a JPEG, PNG or WebP image or a PDF." : "Attach a JPEG, PNG or WebP image."
  return null
}

export function sizeText(bytes: number | null | undefined): string {
  if (bytes == null) return ""
  return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`
}
