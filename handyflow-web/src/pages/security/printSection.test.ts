import { afterEach, describe, it, expect, vi } from "vitest"
import { printCss, printSection } from "./printSection"

describe("printSection", () => {
  afterEach(() => { delete document.body.dataset.printing; vi.restoreAllMocks() })

  it("marks the page as printing that card while the dialog is open, then clears it", () => {
    let during: string | undefined
    const pr = vi.spyOn(window, "print").mockImplementation(() => { during = document.body.dataset.printing })
    printSection("mp")
    expect(pr).toHaveBeenCalledTimes(1)
    expect(during).toBe("mp")
    expect(document.body.dataset.printing).toBe("mp") // still marked until the dialog closes
    window.dispatchEvent(new Event("afterprint"))
    expect(document.body.dataset.printing).toBeUndefined()
  })

  it("clears the mark if printing throws", () => {
    vi.spyOn(window, "print").mockImplementation(() => { throw new Error("blocked") })
    expect(() => printSection("ob")).toThrow("blocked")
    expect(document.body.dataset.printing).toBeUndefined()
  })

  it("a second print replaces the mark", () => {
    vi.spyOn(window, "print").mockImplementation(() => {})
    printSection("ob")
    window.dispatchEvent(new Event("afterprint"))
    printSection("mp")
    expect(document.body.dataset.printing).toBe("mp")
  })

  it("each card's stylesheet only shows its own copy", () => {
    const css = printCss("mp-print", "mp")
    expect(css).toContain("@media screen { .mp-print { display: none; } }")
    expect(css).toContain("@media print {\n  .mp-print { display: none; }")
    expect(css).toContain('body[data-printing="mp"] * { visibility: hidden !important; }')
    expect(css).toContain('body[data-printing="mp"] .mp-print { display: block;')
    expect(css).not.toContain('data-printing="ob"')
  })
})
