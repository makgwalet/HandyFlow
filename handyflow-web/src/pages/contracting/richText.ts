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
