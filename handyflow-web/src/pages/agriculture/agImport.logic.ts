// src/pages/agriculture/agImport.logic.ts
//
// Reading an animal CSV in the browser, before it is sent: delimiter (comma, semicolon or tab, because South African Excel
// saves with semicolons), quotes, a byte-order mark, headers in any order and in the usual spellings. The server checks every
// row again and decides what is created (POST /farms/{id}/animals/import); this only turns the file into rows and says what
// it could not read.

export interface ImportRow {
  tagNumber: string; name: string; species: string; sex: string; breed: string
  dateOfBirth: string; area: string; acquisitionType: string; acquisitionDate: string
}
export type ImportField = keyof ImportRow

export const MAX_IMPORT_ROWS = 500
export const REQUIRED_FIELDS: ImportField[] = ["tagNumber", "species", "sex"]

const HEADER_NAMES: Record<ImportField, string[]> = {
  tagNumber: ["tag", "tag number", "tagnumber", "tag no", "ear tag", "id"],
  name: ["name"],
  species: ["species", "animal", "type"],
  sex: ["sex", "gender"],
  breed: ["breed"],
  dateOfBirth: ["date of birth", "dateofbirth", "dob", "born", "birth date", "birthdate"],
  area: ["area", "production area", "productionarea", "camp", "paddock", "location"],
  acquisitionType: ["acquisition type", "acquisitiontype", "acquisition", "source"],
  acquisitionDate: ["acquisition date", "acquisitiondate", "acquired", "date acquired"],
}
const FIELD_LABELS: Record<ImportField, string> = {
  tagNumber: "Tag number", name: "Name", species: "Species", sex: "Sex", breed: "Breed", dateOfBirth: "Date of birth",
  area: "Production area", acquisitionType: "Acquisition type", acquisitionDate: "Acquisition date",
}
export const fieldLabel = (f: ImportField) => FIELD_LABELS[f]

const normHeader = (h: string) => h.replace(/^\uFEFF/, "").trim().toLowerCase().replace(/[_-]+/g, " ").replace(/\s+/g, " ")

/** The delimiter used on the first line, ignoring anything inside quotes. Comma wins a tie. */
export function detectDelimiter(text: string): "," | ";" | "\t" {
  const first = text.replace(/^\uFEFF/, "").split(/\r?\n/)[0] ?? ""
  const counts: Record<string, number> = { ",": 0, ";": 0, "\t": 0 }
  let quoted = false
  for (const ch of first) {
    if (ch === '"') quoted = !quoted
    else if (!quoted && ch in counts) counts[ch]++
  }
  if (counts[";"] > counts[","] && counts[";"] >= counts["\t"]) return ";"
  if (counts["\t"] > counts[","] && counts["\t"] > counts[";"]) return "\t"
  return ","
}

/** Splits CSV text into rows of cells. Handles quoted cells with delimiters, doubled quotes and line breaks inside quotes. */
export function parseCsv(text: string): string[][] {
  const src = text.replace(/^\uFEFF/, "")
  const delim = detectDelimiter(src)
  const rows: string[][] = []
  let row: string[] = [], cell = "", quoted = false
  for (let i = 0; i < src.length; i++) {
    const ch = src[i]
    if (quoted) {
      if (ch === '"') {
        if (src[i + 1] === '"') { cell += '"'; i++ } else quoted = false
      } else cell += ch
    } else if (ch === '"') quoted = true
    else if (ch === delim) { row.push(cell); cell = "" }
    else if (ch === "\n" || ch === "\r") {
      if (ch === "\r" && src[i + 1] === "\n") i++
      row.push(cell); cell = ""
      rows.push(row); row = []
    } else cell += ch
  }
  if (cell !== "" || row.length > 0) { row.push(cell); rows.push(row) }
  return rows
}

export interface ReadFile {
  rows: ImportRow[]
  /** Why the file cannot be sent at all (no header, a needed column missing, too many rows). Empty when it can. */
  problems: string[]
  /** Columns in the file that are not used. */
  ignored: string[]
}

export function readAnimalCsv(text: string): ReadFile {
  const table = parseCsv(text).filter(r => r.some(c => c.trim() !== ""))
  if (table.length === 0) return { rows: [], problems: ["The file is empty."], ignored: [] }
  const header = table[0].map(normHeader)
  const index: Partial<Record<ImportField, number>> = {}
  const ignored: string[] = []
  header.forEach((h, i) => {
    const field = (Object.keys(HEADER_NAMES) as ImportField[]).find(f => HEADER_NAMES[f].includes(h))
    if (field && index[field] === undefined) index[field] = i
    else if (h !== "") ignored.push(table[0][i].replace(/^\uFEFF/, "").trim())
  })
  const problems: string[] = []
  const missing = REQUIRED_FIELDS.filter(f => index[f] === undefined)
  if (missing.length > 0) problems.push(`The first row must name these columns: ${missing.map(fieldLabel).join(", ")}.`)
  const body = table.slice(1)
  if (missing.length === 0 && body.length === 0) problems.push("The file has a header but no animals.")
  if (body.length > MAX_IMPORT_ROWS) problems.push(`Import at most ${MAX_IMPORT_ROWS} animals at a time (the file has ${body.length}). Split it and import in parts.`)
  if (problems.length > 0) return { rows: [], problems, ignored }
  const get = (r: string[], f: ImportField) => (index[f] === undefined ? "" : (r[index[f] as number] ?? "").trim())
  const rows = body.map(r => ({
    tagNumber: get(r, "tagNumber"), name: get(r, "name"), species: get(r, "species"), sex: get(r, "sex"), breed: get(r, "breed"),
    dateOfBirth: get(r, "dateOfBirth"), area: get(r, "area"), acquisitionType: get(r, "acquisitionType"), acquisitionDate: get(r, "acquisitionDate"),
  }))
  return { rows, problems: [], ignored }
}

export interface ImportRowResult { line: number; tagNumber: string; status: "OK" | "DUPLICATE" | "ERROR"; message: string | null }
export interface ImportResult { dryRun: boolean; total: number; ready: number; duplicates: number; errors: number; rows: ImportRowResult[] }

/** One line under the preview: "12 ready, 2 already on the farm, 1 with a problem." */
export function resultSummary(r: ImportResult): string {
  const parts = [`${r.ready} ready`]
  if (r.duplicates > 0) parts.push(`${r.duplicates} already on the farm or repeated`)
  if (r.errors > 0) parts.push(`${r.errors} with a problem`)
  return `${parts.join(", ")}.`
}

/** The text on the button that does it, or null when there is nothing to import. */
export function importButtonLabel(r: ImportResult | null): string | null {
  if (!r || r.ready === 0) return null
  return `Import ${r.ready} ${r.ready === 1 ? "animal" : "animals"}`
}

/** What to tell the person after the real import. */
export function doneMessage(r: ImportResult): string {
  const skipped = r.duplicates + r.errors
  return `${r.ready} ${r.ready === 1 ? "animal" : "animals"} imported${skipped > 0 ? `, ${skipped} skipped` : ""}.`
}

/** Rows to list in the preview: every problem and duplicate first, in file order. Rows that are fine are only counted. */
export function rowsNeedingAttention(r: ImportResult): ImportRowResult[] {
  return r.rows.filter(x => x.status !== "OK")
}

export const TEMPLATE_CSV =
  "Tag number,Name,Species,Sex,Breed,Date of birth,Production area,Acquisition type,Acquisition date\n" +
  "1001,Daisy,Cattle,Female,Nguni,2024-03-15,North camp,Born on farm,\n" +
  "1002,,Cattle,Male,Bonsmara,15/06/2023,North camp,Purchased,2024-01-10\n"
