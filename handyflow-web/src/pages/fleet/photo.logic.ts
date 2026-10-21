// Which pictures can be used as a vehicle's or driver's photo.
export const PHOTO_MAX_BYTES = 5 * 1024 * 1024
export const PHOTO_TYPES = ["image/jpeg", "image/png", "image/webp"]
/** What is wrong with the chosen picture, or null when it can be uploaded. */
export function photoProblem(file: { type: string; size: number } | null): string | null {
  if (!file) return "Choose a picture."
  if (!PHOTO_TYPES.includes(file.type)) return "Use a JPEG, PNG or WebP picture."
  if (file.size > PHOTO_MAX_BYTES) return "That picture is over 5 MB. Choose a smaller one."
  return null
}
