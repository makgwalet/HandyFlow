import { describe, it, expect, vi } from "vitest"
import type React from "react"
import { rowButtonProps, nextTabIndex } from "./agUi.logic"

const key = (k: string, same = true) => {
  const target = {}
  return { key: k, target, currentTarget: same ? target : {}, preventDefault: vi.fn() } as unknown as React.KeyboardEvent & { preventDefault: ReturnType<typeof vi.fn> }
}

describe("rowButtonProps", () => {
  it("makes a row a focusable button with a name", () => {
    const p = rowButtonProps(() => {}, "Open animal 12")
    expect(p.role).toBe("button")
    expect(p.tabIndex).toBe(0)
    expect(p["aria-label"]).toBe("Open animal 12")
  })
  it("opens on click", () => {
    const open = vi.fn(); rowButtonProps(open, "x").onClick(); expect(open).toHaveBeenCalledTimes(1)
  })
  it("opens on Enter and on Space, and stops the page scrolling on Space", () => {
    const open = vi.fn(); const p = rowButtonProps(open, "x")
    const enter = key("Enter"); p.onKeyDown(enter)
    const space = key(" "); p.onKeyDown(space)
    expect(open).toHaveBeenCalledTimes(2)
    expect(space.preventDefault).toHaveBeenCalled()
  })
  it("ignores other keys", () => {
    const open = vi.fn(); const p = rowButtonProps(open, "x")
    p.onKeyDown(key("a")); p.onKeyDown(key("Tab")); p.onKeyDown(key("Escape"))
    expect(open).not.toHaveBeenCalled()
  })
  it("leaves a button inside the row to handle its own keys", () => {
    const open = vi.fn(); rowButtonProps(open, "x").onKeyDown(key("Enter", false)); expect(open).not.toHaveBeenCalled()
  })
})

describe("nextTabIndex", () => {
  it("moves right and left, wrapping at the ends", () => {
    expect(nextTabIndex("ArrowRight", 0, 4)).toBe(1)
    expect(nextTabIndex("ArrowRight", 3, 4)).toBe(0)
    expect(nextTabIndex("ArrowLeft", 2, 4)).toBe(1)
    expect(nextTabIndex("ArrowLeft", 0, 4)).toBe(3)
  })
  it("jumps to the first and last tab", () => {
    expect(nextTabIndex("Home", 2, 4)).toBe(0)
    expect(nextTabIndex("End", 0, 4)).toBe(3)
  })
  it("ignores other keys and an empty bar", () => {
    expect(nextTabIndex("Enter", 1, 4)).toBeNull()
    expect(nextTabIndex("ArrowDown", 1, 4)).toBeNull()
    expect(nextTabIndex("ArrowRight", 0, 0)).toBeNull()
  })
  it("a single tab stays put", () => {
    expect(nextTabIndex("ArrowRight", 0, 1)).toBe(0)
    expect(nextTabIndex("ArrowLeft", 0, 1)).toBe(0)
  })
})
