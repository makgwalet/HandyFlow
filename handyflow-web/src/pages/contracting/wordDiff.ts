// Word-level comparison inside a changed block, so a reviewer sees which words changed in a paragraph rather than the whole
// paragraph struck out and re-added. Works on plain text, so the result is safe to render without HTML.

export type WordOp = { type: 'same' | 'del' | 'add'; text: string }

const ENTITIES: Record<string, string> = { '&nbsp;': ' ', '&amp;': '&', '&lt;': '<', '&gt;': '>', '&quot;': '"', '&#39;': "'" }

/** Visible text of an HTML fragment: tags removed, common entities decoded, whitespace collapsed. */
export function toText(html: string): string {
  return html.replace(/<br\s*\/?>/gi, ' ').replace(/<\/(p|h[1-6]|li|blockquote|div)>/gi, ' ').replace(/<[^>]*>/g, '')
    .replace(/&(nbsp|amp|lt|gt|quot|#39);/g, m => ENTITIES[m] ?? m).replace(/\s+/g, ' ').trim()
}

/** True for plain text blocks (paragraphs, headings, quotes, list items) that contain no table or nested list. */
export function isSimpleBlock(html: string): boolean {
  return /^\s*<(p|h[1-6]|li|blockquote)\b/i.test(html) && !/<(table|ul|ol|hr)\b/i.test(html)
}

const tokens = (s: string): string[] => s.match(/\s+|[^\s]+/g) ?? []

/** Largest size of the comparison table; bigger blocks fall back to the block-level view. */
const MAX_CELLS = 250_000

/** Word-by-word difference, or null when the texts are too large to compare quickly. */
export function wordDiff(oldText: string, newText: string): WordOp[] | null {
  const a = tokens(oldText), b = tokens(newText)
  if ((a.length + 1) * (b.length + 1) > MAX_CELLS) return null
  const w = b.length + 1
  const lcs = new Uint32Array((a.length + 1) * w)
  for (let i = a.length - 1; i >= 0; i--)
    for (let j = b.length - 1; j >= 0; j--)
      lcs[i * w + j] = a[i] === b[j] ? lcs[(i + 1) * w + j + 1] + 1 : Math.max(lcs[(i + 1) * w + j], lcs[i * w + j + 1])
  const ops: WordOp[] = []
  const push = (type: WordOp['type'], text: string) => {
    const last = ops[ops.length - 1]
    if (last && last.type === type) last.text += text; else ops.push({ type, text })
  }
  let i = 0, j = 0
  while (i < a.length && j < b.length) {
    if (a[i] === b[j]) { push('same', a[i]); i++; j++ }
    else if (lcs[(i + 1) * w + j] >= lcs[i * w + j + 1]) { push('del', a[i]); i++ }
    else { push('add', b[j]); j++ }
  }
  while (i < a.length) push('del', a[i++])
  while (j < b.length) push('add', b[j++])
  return ops
}

/**
 * Word-level view of a changed hunk: the removed and added blocks as text with the changed words marked, or null when the hunk
 * has a table or list, has nothing on one side, or is too large (the caller then shows whole blocks).
 */
export function hunkWordDiff(removed: string[], added: string[]): WordOp[] | null {
  if (removed.length === 0 || added.length === 0) return null
  if (![...removed, ...added].every(isSimpleBlock)) return null
  return wordDiff(removed.map(toText).join('\n'), added.map(toText).join('\n'))
}
