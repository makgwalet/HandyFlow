// A practitioner's name with exactly one "Dr." in front (patch 0202).
// Some stored names already start with "Dr" or "Dr.", which used to print as "Dr. Dr Sarah Mokoena".
// Only a whole word "Dr" counts as the title, so a name like "Drew Smith" still gets one.

const TITLE = /^\s*dr\b\.?\s*/i

export function drName(name: string | null | undefined): string {
  const bare = (name ?? "").replace(TITLE, "").trim()
  return bare ? `Dr. ${bare}` : ""
}
