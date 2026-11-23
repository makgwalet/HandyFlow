import { describe, it, expect } from "vitest"
import { readFileSync } from "node:fs"

// SecurityPage is too heavy to render in a unit test (every tab loads); this checks the banner is mounted above the section content.
describe("SecurityPage shows the limited-access banner", () => {
  const src = readFileSync("src/pages/security/SecurityPage.tsx", "utf8")
  it("imports and renders it before the section content", () => {
    expect(src).toContain('import MySiteScopeBanner from "./MySiteScopeBanner"')
    const banner = src.indexOf("<MySiteScopeBanner />")
    expect(banner).toBeGreaterThan(0)
    expect(banner).toBeGreaterThan(src.indexOf("<PageHeader"))
    expect(banner).toBeLessThan(src.indexOf("{content[section.id]}"))
  })
})
