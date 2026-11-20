// src/pages/legalcompliance/customReportLogic.ts
//
// Pure helpers for the custom report editor. The server checks every rule again.

export type Op = "EQUALS" | "NOT_EQUALS" | "CONTAINS" | "NOT_CONTAINS" | "STARTS_WITH" | "GT" | "GTE" | "LT" | "LTE" | "EMPTY" | "NOT_EMPTY"
export interface FilterSpec { column: string; op: Op; value: string }
export const OPS: { value: Op; label: string; needsValue: boolean }[] = [
  { value: "EQUALS", label: "is", needsValue: true },
  { value: "NOT_EQUALS", label: "is not", needsValue: true },
  { value: "CONTAINS", label: "contains", needsValue: true },
  { value: "NOT_CONTAINS", label: "does not contain", needsValue: true },
  { value: "STARTS_WITH", label: "starts with", needsValue: true },
  { value: "GT", label: "is more than", needsValue: true },
  { value: "GTE", label: "is at least", needsValue: true },
  { value: "LT", label: "is less than", needsValue: true },
  { value: "LTE", label: "is at most", needsValue: true },
  { value: "EMPTY", label: "is empty", needsValue: false },
  { value: "NOT_EMPTY", label: "is not empty", needsValue: false },
]
export const MAX_FILTERS = 10

/** Why the editor cannot save yet, or null. The server checks everything again. */
export function editorProblem(name: string, baseKey: string, cols: string[], filters: FilterSpec[]): string | null {
  if (name.trim().length < 3) return "Give the report a name of at least 3 characters"
  if (!baseKey) return "Choose the standard report this is built on"
  if (cols.length === 0) return "Choose at least one column"
  for (const f of filters) {
    if (!f.column) return "Every filter needs a column"
    if (OPS.find(o => o.value === f.op)?.needsValue && f.value.trim() === "") return `The filter on "${f.column}" needs a value`
  }
  return null
}

export function moveItem<T>(list: T[], index: number, by: -1 | 1): T[] {
  const to = index + by
  if (index < 0 || index >= list.length || to < 0 || to >= list.length) return list
  const next = [...list]
  ;[next[index], next[to]] = [next[to], next[index]]
  return next
}
