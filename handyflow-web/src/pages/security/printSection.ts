// Printing one card of a page that has several print-ready cards.
//
// Each card keeps a hidden print copy in the page. Pressing a card's print button marks the page as printing that card
// (body[data-printing="key"]), and only that card's copy is shown on paper. Plain Ctrl+P (no mark) prints the page as it is.

/** The print stylesheet of a card whose print copy has the given class and key. */
export function printCss(cls: string, key: string): string {
  const on = `body[data-printing="${key}"]`
  return `
@media screen { .${cls} { display: none; } }
@media print {
  .${cls} { display: none; }
  ${on} * { visibility: hidden !important; }
  ${on} .${cls}, ${on} .${cls} * { visibility: visible !important; }
  ${on} .${cls} { display: block; position: absolute; left: 0; top: 0; width: 100%; color: #000; background: #fff; font-size: 12px; }
  ${on} .${cls} table { width: 100%; border-collapse: collapse; }
  ${on} .${cls} th, ${on} .${cls} td { border-bottom: 1px solid #999; padding: 4px 6px; text-align: left; vertical-align: top; }
  ${on} .${cls} tr { page-break-inside: avoid; }
}`
}

/** Prints the card with this key, then clears the mark when the print dialog closes. */
export function printSection(key: string): void {
  const body = document.body
  body.dataset.printing = key
  const clear = () => { delete body.dataset.printing; window.removeEventListener("afterprint", clear) }
  window.addEventListener("afterprint", clear)
  try { window.print() } catch (e) { clear(); throw e }
}
