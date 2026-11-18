// Helpers for the rich-text editor. Kept apart from the component so they can be tested without a browser editor.

/** The editor emits "<p></p>" for an empty document; treat that (and whitespace) as no text at all. */
export function normalizeEditorHtml(html: string): string {
  const trimmed = html.trim()
  if (trimmed === '' || /^(<p>\s*<\/p>|<p><br\s*\/?><\/p>)$/i.test(trimmed)) return ''
  return html
}

/** True when the HTML has any visible text, an image-free structural element such as a table or rule, or a variable placeholder. */
export function hasContent(html: string): boolean {
  const n = normalizeEditorHtml(html)
  if (!n) return false
  return /<(table|hr)\b/i.test(n) || n.replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim().length > 0
}

/** Tags and attributes the editor keeps. Anything else in the text is simplified when the text passes through the editor. */
const KEPT_TAGS = new Set(['p', 'br', 'strong', 'b', 'em', 'i', 'u', 'h1', 'h2', 'h3', 'ul', 'ol', 'li', 'blockquote', 'hr', 'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td', 'colgroup', 'col'])

/**
 * Names the markup in `html` that the rich-text editor would simplify (for example div and span wrappers, inline styles and classes),
 * so the person can be warned before editing and can use the HTML view to keep the text exactly as it is.
 */
export function unsupportedMarkup(html: string): string[] {
  const found = new Set<string>()
  for (const m of html.matchAll(/<\/?([a-zA-Z][a-zA-Z0-9]*)\b([^>]*)>/g)) {
    const tag = m[1].toLowerCase()
    if (!KEPT_TAGS.has(tag)) found.add(`<${tag}>`)
    if (/\b(style|class|align|color|face|size)\s*=/i.test(m[2])) found.add(tag === 'td' || tag === 'th' ? 'cell styling' : 'style or class attributes')
  }
  return Array.from(found).sort()
}
