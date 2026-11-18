import DOMPurify from 'dompurify'

// Contract and template bodies are HTML. Anything rendered with dangerouslySetInnerHTML goes through this
// first, so stored script (including bodies saved before the server started sanitising) cannot run.
// Keep the tag list in step with HtmlSanitizer.java on the server.
const ALLOWED_TAGS = [
  'p', 'div', 'span', 'br', 'hr', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'strong', 'b', 'em', 'i', 'u', 'sub', 'sup', 'blockquote',
  'ul', 'ol', 'li', 'table', 'thead', 'tbody', 'tr', 'th', 'td',
]

export function sanitizeHtml(html: string | null | undefined): string {
  if (!html) return ''
  return DOMPurify.sanitize(html, { ALLOWED_TAGS, ALLOWED_ATTR: ['colspan', 'rowspan', 'style'] })
}
