// src/pages/agriculture/agPatch.ts
// Builds the body of an edit request so it carries only what the person changed.
// The server keeps every field that is left out, and empties the ones named in `clear`.
// (Before, the screen sent every field it knew about, with the ones it did not show as null,
// so saving a note wiped the sire, dam, dates and paddock.)

export interface TextPatch {
  [field: string]: string | string[] | undefined
  clear?: string[]
}

export function buildTextPatch(
  original: Record<string, string | null | undefined>,
  edited: Record<string, string>,
): TextPatch | null {
  const patch: TextPatch = {}
  const clear: string[] = []
  for (const field of Object.keys(edited)) {
    const before = (original[field] ?? "").trim()
    const after = edited[field].trim()
    if (before === after) continue
    if (after === "") clear.push(field)
    else patch[field] = after
  }
  if (clear.length) patch.clear = clear
  return Object.keys(patch).length === 0 ? null : patch
}
