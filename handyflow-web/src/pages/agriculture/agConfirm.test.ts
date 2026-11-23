import { describe, it, expect, vi, afterEach } from "vitest"
import { agConfirm, subscribeConfirm, currentConfirm } from "./agConfirm"

describe("agConfirm", () => {
  afterEach(() => vi.unstubAllGlobals())

  it("falls back to the browser confirm when no host is mounted", async () => {
    vi.stubGlobal("window", { confirm: vi.fn(() => true) })
    expect(await agConfirm({ title: "Delete?" })).toBe(true)
  })

  it("resolves with the host's answer and clears the question", async () => {
    const seen: Array<string | null> = []
    const off = subscribeConfirm(p => seen.push(p ? p.title : null))
    const q = agConfirm({ title: "Delete farm?", danger: true })
    expect(currentConfirm()?.title).toBe("Delete farm?")
    currentConfirm()!.resolve(true)
    expect(await q).toBe(true)
    expect(currentConfirm()).toBeNull()
    expect(seen).toEqual([null, "Delete farm?", null])
    off()
  })

  it("a new question answers the unanswered one with no", async () => {
    const off = subscribeConfirm(() => {})
    const first = agConfirm({ title: "A" })
    const second = agConfirm({ title: "B" })
    expect(await first).toBe(false)
    currentConfirm()!.resolve(true)
    expect(await second).toBe(true)
    off()
  })

  it("answering no resolves false", async () => {
    const off = subscribeConfirm(() => {})
    const q = agConfirm({ title: "X" })
    currentConfirm()!.resolve(false)
    expect(await q).toBe(false)
    off()
  })
})
