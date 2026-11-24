// @vitest-environment jsdom
import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), delete: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))

import { useAuthStore } from "../../store/auth.store"
import CoverageTab from "./CoverageTab"

const row = (o: Record<string, unknown> = {}) => ({
  siteId: "s1", siteName: "Mall", postId: "p1", postName: "Main gate", day: "2026-11-20", start: "2026-11-20T16:00:00Z", end: "2026-11-21T04:00:00Z",
  needed: 2, state: "SHORT", gaps: [{ start: "2026-11-20T22:00:00Z", end: "2026-11-21T02:00:00Z", have: 1, need: 2 }], ...o,
})
const board = (rows: unknown[], o: Record<string, unknown> = {}) => ({ from: "2026-11-20", days: 7, postCount: 1, shortCount: rows.length, uncoveredCount: 0, rows, ...o })
const posts = [{ id: "p1", siteId: "s1", name: "Main gate", weekdays: [1, 2, 3, 4, 5], start: "18:00:00", end: "06:00:00", minGuards: 2 }]

function route(boardRes: unknown, postsRes: unknown = posts) {
  api.get.mockImplementation((url: string) => {
    if (url.includes("/sites?size")) return Promise.resolve({ data: { data: { content: [{ id: "s1", name: "Mall" }] } } })
    if (url.endsWith("/coverage")) return boardRes instanceof Error ? Promise.reject(boardRes) : Promise.resolve({ data: { data: boardRes } })
    if (url.endsWith("/coverage-posts")) return Promise.resolve({ data: { data: postsRes } })
    if (url.endsWith("/weekly-hours")) return Promise.resolve({ data: { data: { weekStart: "2026-11-16", guardsRostered: 0, flagged: 0, thresholds: { warnAt: 40, limit: 45, ceiling: 55, minRestHours: 12 }, truncated: false, rows: [] } } })
    if (url.endsWith("/working-time-policies")) return Promise.resolve({ data: { data: [] } })
    if (url.endsWith("/company-time-zone")) return Promise.resolve({ data: { data: { timeZone: "Africa/Johannesburg", abbreviation: "SAST", now: "22 Nov 2026 19:40 SAST", today: "2026-11-22" } } })
    return Promise.reject(new Error("unexpected " + url))
  })
}
function as(perms: string[]) { useAuthStore.setState({ user: { permissions: perms } } as never) }
function show() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><CoverageTab /></QueryClientProvider>)
}
async function pickSite() {
  const sel = (await screen.findByLabelText("Posts for site")) as HTMLSelectElement
  await waitFor(() => expect(sel.options.length).toBeGreaterThan(1)) // the site list has loaded
  fireEvent.change(sel, { target: { value: "s1" } })
}

describe("CoverageTab", () => {
  beforeEach(() => { api.get.mockReset(); api.post.mockReset(); api.delete.mockReset(); api.post.mockResolvedValue({ data: {} }); api.delete.mockResolvedValue({ data: {} }) })
  afterEach(() => cleanup())

  it("shows the working-time policies under the weekly hours", async () => {
    as(["SECURITY_READ"]); route(board([]))
    show()
    expect(await screen.findByRole("region", { name: "Working-time policies" })).toBeTruthy()
    expect(screen.getByRole("region", { name: "Weekly hours" })).toBeTruthy()
  })

  it("lists short posts with the gap and who is missing", async () => {
    as(["SECURITY_READ"]); route(board([row()]))
    show()
    expect(await screen.findByText("Short")).toBeTruthy()
    expect(screen.getByText("18:00–06:00")).toBeTruthy()
    expect(screen.getByText("00:00–04:00: 1 of 2 guards")).toBeTruthy()
    expect(screen.getByText("1 post-day is short over the next 7 days.")).toBeTruthy()
  })

  it("marks a day with nobody rostered", async () => {
    as(["SECURITY_READ"]); route(board([row({ state: "UNCOVERED", gaps: [{ start: "2026-11-20T16:00:00Z", end: "2026-11-21T04:00:00Z", have: 0, need: 2 }] })], { shortCount: 0, uncoveredCount: 1 }))
    show()
    expect(await screen.findByText("No one rostered")).toBeTruthy()
    expect(screen.getByText(/1 with no one rostered/)).toBeTruthy()
  })

  it("says all covered only when there are posts and no gaps", async () => {
    as(["SECURITY_READ"]); route(board([row({ state: "COVERED", gaps: [] })], { shortCount: 0 }))
    show()
    expect(await screen.findByText("Every post is covered for the next 7 days.")).toBeTruthy()
  })

  it("with no posts it says there is nothing to measure, not that all is well", async () => {
    as(["SECURITY_READ"]); route(board([], { postCount: 0, shortCount: 0 }))
    show()
    expect(await screen.findByText(/nothing to measure/)).toBeTruthy()
    expect(screen.queryByText(/Every post is covered/)).toBeNull()
  })

  it("the weekly hours panel is part of the screen", async () => {
    as(["SECURITY_READ"]); route(board([]))
    show()
    expect(await screen.findByLabelText("Weekly hours")).toBeTruthy()
    expect(await screen.findByText("No guards are rostered this week.")).toBeTruthy()
  })

  it("shows an error, not a clean board, when loading fails", async () => {
    as(["SECURITY_READ"]); route(new Error("x"))
    show()
    await waitFor(() => expect(screen.getAllByRole("alert").length).toBeGreaterThan(0))
    expect(screen.queryByText(/Every post is covered/)).toBeNull()
  })

  it("changing the days or site asks the server for exactly that", async () => {
    as(["SECURITY_READ"]); route(board([]))
    show()
    await screen.findByText(/nothing to measure|covered/)
    fireEvent.change(screen.getByLabelText("Days ahead"), { target: { value: "14" } })
    fireEvent.change(screen.getByLabelText("Site"), { target: { value: "s1" } })
    await waitFor(() => {
      const calls = api.get.mock.calls.filter(c => String(c[0]).endsWith("/coverage"))
      expect(calls[calls.length - 1][1].params).toEqual({ days: 14, siteId: "s1" })
    })
  })

  it("read-only users see posts but cannot add or remove", async () => {
    as(["SECURITY_READ"]); route(board([]))
    show(); await pickSite()
    expect(await screen.findByText("Main gate")).toBeTruthy()
    expect(screen.getByText(/Mon–Fri · 18:00–06:00 \(past midnight\) · 2 guards/)).toBeTruthy()
    expect(screen.queryByText("Add post")).toBeNull()
    expect(screen.queryByLabelText("Remove Main gate")).toBeNull()
  })

  it("managers add a post with the chosen days, times and guards", async () => {
    as(["SECURITY_MANAGE"]); route(board([]))
    show(); await pickSite()
    fireEvent.change(await screen.findByLabelText("Post name"), { target: { value: " Rear gate " } })
    fireEvent.change(screen.getByLabelText("Starts"), { target: { value: "20:00" } })
    fireEvent.change(screen.getByLabelText("Ends"), { target: { value: "04:00" } })
    fireEvent.change(screen.getByLabelText("Guards needed"), { target: { value: "3" } })
    fireEvent.click(screen.getByLabelText("Sat"))
    fireEvent.click(screen.getByText("Add post"))
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1))
    expect(api.post.mock.calls[0][0]).toBe("/api/v1/security/sites/s1/coverage-posts")
    expect(api.post.mock.calls[0][1]).toEqual({ name: "Rear gate", weekdays: [1, 2, 3, 4, 5, 6], start: "20:00", end: "04:00", minGuards: 3 })
  })

  it("refuses an invalid post without calling the server", async () => {
    as(["SECURITY_MANAGE"]); route(board([]))
    show(); await pickSite()
    fireEvent.click(await screen.findByText("Add post"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/name/)
    expect(api.post).not.toHaveBeenCalled()
  })

  it("shows the server message when adding fails", async () => {
    as(["SECURITY_MANAGE"]); route(board([]))
    api.post.mockImplementation(() => Promise.reject({ response: { data: { message: "Site not found" } } }))
    show(); await pickSite()
    fireEvent.change(await screen.findByLabelText("Post name"), { target: { value: "Gate" } })
    fireEvent.click(screen.getByText("Add post"))
    expect((await screen.findByRole("alert")).textContent).toBe("Site not found")
  })

  it("removing a post calls delete for that post", async () => {
    as(["SECURITY_MANAGE"]); route(board([]))
    show(); await pickSite()
    fireEvent.click(await screen.findByLabelText("Remove Main gate"))
    await waitFor(() => expect(api.delete).toHaveBeenCalledWith("/api/v1/security/sites/s1/coverage-posts/p1"))
  })
})
