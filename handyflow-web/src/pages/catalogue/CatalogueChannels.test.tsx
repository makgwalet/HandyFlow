import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ChannelsPanel from "./ChannelsPanel"
import ChannelsTab from "./ChannelsTab"
import { channelBody, channelProblems, EMPTY_CHANNEL_FORM, publishText, type Channel, type ItemChannel, type PublishResult } from "./catalogue.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (list: string[]) => list.some(p => perms.has(p)) }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
beforeEach(() => { perms = new Set(["CATALOGUE_PUBLISH"]); for (const m of [get, post, put, del]) m.mockReset(); post.mockResolvedValue(ok({})); put.mockResolvedValue(ok({})); del.mockResolvedValue(ok(null)) })
afterEach(cleanup)

const channel = (over: Partial<Channel> = {}): Channel => ({ id: "c1", code: "web-shop", name: "Garden shop", kind: "WEB", priceListId: null, priceListName: null, active: true, publishedCount: 3, ...over })
const result = (over: Partial<PublishResult> = {}): PublishResult => ({ channelId: "c1", dryRun: false, done: 1, unchanged: 0, blocked: 1, rows: [
  { itemId: "i1", name: "Cement", sku: "CEM", outcome: "PUBLISHED", problems: [] }, { itemId: "i2", name: "Sand", sku: "SND", outcome: "BLOCKED", problems: ["It needs a picture."] }], ...over })

describe("channel logic", () => {
  it("checks the form", () => {
    expect(channelProblems({ ...EMPTY_CHANNEL_FORM, code: "Web Shop", name: "" }, true)).toEqual({ code: "2 to 30 letters, digits or hyphens, e.g. web-shop", name: "Required" })
    expect(channelProblems({ ...EMPTY_CHANNEL_FORM, code: "web-shop", name: "Shop" }, true)).toEqual({})
    expect(channelProblems({ ...EMPTY_CHANNEL_FORM, code: "", name: "Shop" }, false)).toEqual({})   // the code is fixed after creation
    expect(channelProblems({ ...EMPTY_CHANNEL_FORM, name: "x".repeat(81) }, false).name).toContain("80")
  })
  it("builds the body", () => {
    expect(channelBody({ code: " Web-Shop ", name: " Shop ", kind: "WEB", priceListId: "", active: true })).toEqual({ code: "web-shop", name: "Shop", kind: "WEB", priceListId: null, active: true })
  })
  it("sums up a publish or a check", () => {
    expect(publishText(result())).toBe("1 published, 1 cannot be published.")
    expect(publishText(result({ dryRun: true, unchanged: 2, blocked: 0 }))).toBe("1 can be published, 2 already there.")
    expect(publishText(result({ blocked: 0 }), "unpublish")).toBe("1 taken off.")
  })
})

describe("ChannelsPanel", () => {
  it("lists channels with counts and offers management to a publisher", async () => {
    get.mockImplementation(() => ok([channel(), channel({ id: "c2", code: "till", name: "Till", kind: "POS", active: false, publishedCount: 0 })]))
    wrap(<ChannelsPanel />)
    expect(await screen.findByText("Garden shop")).toBeTruthy()
    expect(screen.getByRole("button", { name: "Add channel" })).toBeTruthy()
    expect(screen.getByRole("button", { name: "Edit till" })).toBeTruthy()
  })
  it("shows a reader the list without the controls", async () => {
    perms = new Set()
    get.mockImplementation(() => ok([channel()]))
    wrap(<ChannelsPanel />)
    expect(await screen.findByText("Garden shop")).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Add channel" })).toBeNull()
    expect(screen.queryByRole("button", { name: "Edit web-shop" })).toBeNull()
    expect(screen.getByRole("button", { name: "Items on web-shop" })).toBeTruthy()
  })
  it("checks the new channel before sending it", async () => {
    get.mockImplementation((url: string) => ok(url.includes("price-lists") ? [] : []))
    wrap(<ChannelsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Add channel" }))
    fireEvent.click(screen.getByRole("button", { name: "Create channel" }))
    expect(await screen.findByText(/2 to 30 letters/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText(/Code/), { target: { value: "web-shop" } })
    fireEvent.change(screen.getByLabelText(/Name/), { target: { value: "Online shop" } })
    fireEvent.click(screen.getByRole("button", { name: "Create channel" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/catalogue/channels", { code: "web-shop", name: "Online shop", kind: "WEB", priceListId: null, active: true }))
  })
  it("says why a channel cannot be deleted", async () => {
    get.mockImplementation(() => ok([channel()]))
    del.mockRejectedValue({ response: { data: { message: "This channel has 3 items published to it." } } })
    wrap(<ChannelsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Delete web-shop" }))
    expect(await screen.findByText(/3 items published/)).toBeTruthy()
  })
  it("checks items first, shows the reasons, then publishes", async () => {
    get.mockImplementation((url: string) => {
      if (url.endsWith("/items")) return ok([{ itemId: "i9", name: "Brick", sku: "BR", status: "ACTIVE", publishedAt: "2026-10-21T08:00:00Z" }])
      if (url.includes("/items/search")) return ok({ content: [{ id: "i1", name: "Cement", sku: "CEM", parentId: null }, { id: "i2", name: "Sand", sku: "SND", parentId: null }], totalElements: 2 })
      return ok([channel()])
    })
    post.mockImplementation((_u: string, body: { dryRun: boolean }) => ok(result({ dryRun: body.dryRun, rows: [
      { itemId: "i1", name: "Cement", sku: "CEM", outcome: body.dryRun ? "WOULD_PUBLISH" : "PUBLISHED", problems: [] }, { itemId: "i2", name: "Sand", sku: "SND", outcome: "BLOCKED", problems: ["It needs a picture."] }] })))
    wrap(<ChannelsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Items on web-shop" }))
    expect(await screen.findByText("Brick")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Find items to publish"), { target: { value: "ce" } })
    fireEvent.click(await screen.findByRole("button", { name: "Cement · CEM" }))
    fireEvent.change(screen.getByLabelText("Find items to publish"), { target: { value: "sa" } })
    fireEvent.click(await screen.findByRole("button", { name: "Sand · SND" }))
    fireEvent.click(screen.getByRole("button", { name: "Check" }))
    expect(await screen.findByText(/Can be published/)).toBeTruthy()
    expect(post).toHaveBeenCalledWith("/api/v1/catalogue/channels/c1/publish", { itemIds: ["i1", "i2"], dryRun: true })
    fireEvent.click(screen.getByRole("button", { name: "Publish 2 items" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/catalogue/channels/c1/publish", { itemIds: ["i1", "i2"], dryRun: false }))
    expect(await screen.findByText(/1 published, 1 cannot be published/)).toBeTruthy()
    expect(screen.getByText(/It needs a picture\./)).toBeTruthy()
  })
  it("takes an item off", async () => {
    get.mockImplementation((url: string) => url.endsWith("/items") ? ok([{ itemId: "i9", name: "Brick", sku: "BR", status: "ARCHIVED", publishedAt: "2026-10-21T08:00:00Z" }]) : ok([channel()]))
    post.mockResolvedValue(ok(result({ done: 1, blocked: 0, rows: [{ itemId: "i9", name: "Brick", sku: "BR", outcome: "UNPUBLISHED", problems: [] }] })))
    wrap(<ChannelsPanel />)
    fireEvent.click(await screen.findByRole("button", { name: "Items on web-shop" }))
    expect(await screen.findByText(/Not live \(archived\)/)).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Take Brick off" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/catalogue/channels/c1/unpublish", { itemIds: ["i9"], dryRun: false }))
  })
})

describe("ChannelsTab", () => {
  const rows = (): ItemChannel[] => [
    { channelId: "c1", code: "web-shop", name: "Online shop", kind: "WEB", channelActive: true, published: true, publishedAt: "2026-10-21T08:00:00Z", problems: [] },
    { channelId: "c2", code: "till", name: "Till", kind: "POS", channelActive: true, published: false, publishedAt: null, problems: [] },
    { channelId: "c3", code: "market", name: "Marketplace", kind: "MARKETPLACE", channelActive: true, published: false, publishedAt: null, problems: ["It needs a picture."] },
  ]
  it("shows where the item is, what is ready and what is not", async () => {
    get.mockImplementation(() => ok(rows()))
    wrap(<ChannelsTab itemId="i1" canPublish />)
    expect(await screen.findByText("Online shop")).toBeTruthy()
    expect(screen.getByText("Ready to publish")).toBeTruthy()
    expect(screen.getByText(/Not ready: It needs a picture\./)).toBeTruthy()
    expect((screen.getByRole("button", { name: "Publish to Marketplace" }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByRole("button", { name: "Publish to Till" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/catalogue/channels/c2/publish", { itemIds: ["i1"], dryRun: false }))
    fireEvent.click(screen.getByRole("button", { name: "Take off Online shop" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/catalogue/channels/c1/unpublish", { itemIds: ["i1"], dryRun: false }))
  })
  it("gives a reader no buttons", async () => {
    get.mockImplementation(() => ok(rows()))
    wrap(<ChannelsTab itemId="i1" canPublish={false} />)
    const li = (await screen.findByText("Online shop")).closest("li") as HTMLElement
    expect(within(li).queryByRole("button")).toBeNull()
  })
  it("reports a refusal from the server", async () => {
    get.mockImplementation(() => ok(rows()))
    post.mockResolvedValue(ok(result({ rows: [{ itemId: "i1", name: "Cement", sku: "CEM", outcome: "BLOCKED", problems: ["Only active items can be published."] }] })))
    wrap(<ChannelsTab itemId="i1" canPublish />)
    fireEvent.click(await screen.findByRole("button", { name: "Publish to Till" }))
    expect(await screen.findByText(/Only active items can be published/)).toBeTruthy()
  })
})
