import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import MediaTab from "./MediaTab"
import PackageTab, { UsedInList } from "./PackageTab"
import { bundleBody, bundleProblem, fileSizeText, mediaProblem, savingText, type BundleView, type MediaFile } from "./catalogue.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
beforeEach(() => { for (const m of [get, post, put, del]) m.mockReset(); post.mockResolvedValue(ok({})); put.mockResolvedValue(ok({})); del.mockResolvedValue(ok(null)) })
afterEach(cleanup)

const file = (over: Partial<MediaFile> = {}): MediaFile => ({
  id: "m1", itemId: "i1", kind: "IMAGE", title: "Front", altText: null, fileName: "front.png", contentType: "image/png", sizeBytes: 2048, primary: true, sortOrder: 0,
  uploadedAt: "2026-10-21T08:00:00Z", url: "/api/v1/catalogue/items/i1/media/m1/file", ...over,
})
const bundle = (over: Partial<BundleView> = {}): BundleView => ({
  bundleId: "p1", name: "Bathroom kit", sku: "BK", type: "PACKAGE", price: 200, partsTotal: 225.5, partsCost: 130, saving: 25.5, unpricedParts: 0,
  components: [{ itemId: "t1", name: "Tap", sku: "TAP", status: "ACTIVE", quantity: 2, unitPrice: 100, lineTotal: 200 }, { itemId: "t2", name: "Pipe", sku: "PIP", status: "ACTIVE", quantity: 1, unitPrice: 25.5, lineTotal: 25.5 }], ...over,
})

describe("media and package logic", () => {
  it("checks a file before it is sent", () => {
    expect(mediaProblem({ name: "a.png", type: "image/png", size: 1000 })).toBe("")
    expect(mediaProblem({ name: "a.pdf", type: "application/pdf", size: 9 * 1024 * 1024 })).toBe("")
    expect(mediaProblem({ name: "a.svg", type: "image/svg+xml", size: 10 })).toContain("JPEG")
    expect(mediaProblem({ name: "a.png", type: "image/png", size: 6 * 1024 * 1024 })).toContain("5 MB")
    expect(mediaProblem({ name: "a.pdf", type: "application/pdf", size: 11 * 1024 * 1024 })).toContain("10 MB")
    expect(mediaProblem({ name: "a.png", type: "image/png", size: 0 })).toContain("empty")
  })
  it("writes sizes plainly", () => {
    expect(fileSizeText(500)).toBe("500 B")
    expect(fileSizeText(2048)).toBe("2 KB")
    expect(fileSizeText(3 * 1024 * 1024)).toBe("3.0 MB")
  })
  it("checks the parts list", () => {
    expect(bundleProblem([], "p")).toContain("at least one")
    expect(bundleProblem([{ itemId: "p", quantity: "1" }], "p")).toContain("itself")
    expect(bundleProblem([{ itemId: "a", quantity: "1" }, { itemId: "a", quantity: "2" }], "p")).toContain("only once")
    expect(bundleProblem([{ itemId: "a", quantity: "0" }], "p")).toContain("more than zero")
    expect(bundleProblem([{ itemId: "a", quantity: "x" }], "p")).toContain("more than zero")
    expect(bundleProblem([{ itemId: "a", quantity: "1,2345" }], "p")).toContain("3 decimals")
    expect(bundleProblem([{ itemId: "a", quantity: "10000" }], "p")).toContain("9999.999")
    expect(bundleProblem([{ itemId: "a", quantity: "1,5" }, { itemId: "b", quantity: "2" }], "p")).toBe("")
    expect(bundleBody([{ itemId: "a", quantity: "1,5" }])).toEqual({ components: [{ itemId: "a", quantity: 1.5 }] })
  })
  it("explains the saving", () => {
    expect(savingText(25.5, 0)).toContain("save")
    expect(savingText(-10, 0)).toContain("more than its parts")
    expect(savingText(0, 0)).toContain("same")
    expect(savingText(null, 2)).toContain("2 parts have no price")
    expect(savingText(null, 1)).toContain("1 part has no price")
  })
})

describe("MediaTab", () => {
  it("lists pictures and documents and offers to change them to a writer", async () => {
    get.mockImplementation((url: string) => url.endsWith("/media") ? ok([file(), file({ id: "m2", fileName: "back.png", title: null, primary: false }), file({ id: "d1", kind: "DOCUMENT", fileName: "spec.pdf", title: null, contentType: "application/pdf", primary: false })]) : Promise.reject(new Error("no blob")))
    wrap(<MediaTab itemId="i1" canWrite />)
    expect(await screen.findByText("Front")).toBeTruthy()
    expect(screen.getByText(/Main picture/)).toBeTruthy()
    expect(screen.getByRole("button", { name: "Remove spec.pdf" })).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Make main" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/catalogue/items/i1/media/m2", { primary: true }))
    fireEvent.click(screen.getByRole("button", { name: "Remove spec.pdf" }))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/catalogue/items/i1/media/d1"))
  })
  it("shows no controls to someone who cannot write", async () => {
    get.mockImplementation(() => ok([file()]))
    wrap(<MediaTab itemId="i1" canWrite={false} />)
    expect(await screen.findByText("Front")).toBeTruthy()
    expect(screen.queryByLabelText("Choose a picture or PDF")).toBeNull()
    expect(screen.queryByRole("button", { name: /Remove/ })).toBeNull()
    expect(screen.queryByRole("button", { name: "Make main" })).toBeNull()
  })
  it("refuses a wrong file without calling the server and sends a good one", async () => {
    get.mockImplementation(() => ok([]))
    wrap(<MediaTab itemId="i1" canWrite />)
    expect(await screen.findByText("No pictures or documents yet.")).toBeTruthy()
    const input = screen.getByLabelText("Choose a picture or PDF")
    fireEvent.change(input, { target: { files: [new File(["x"], "a.svg", { type: "image/svg+xml" })] } })
    expect(await screen.findByText(/Use a JPEG, PNG or WebP/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(input, { target: { files: [new File(["x"], "a.png", { type: "image/png" })] } })
    await waitFor(() => expect(post).toHaveBeenCalledTimes(1))
    expect(post.mock.calls[0][0]).toBe("/api/v1/catalogue/items/i1/media")
  })
})

describe("PackageTab", () => {
  it("shows the parts, their worth and the saving", async () => {
    get.mockImplementation(() => ok(bundle()))
    wrap(<PackageTab itemId="p1" canWrite onChanged={() => undefined} />)
    expect(await screen.findByText("Tap")).toBeTruthy()
    expect(screen.getByText(/Customers save/)).toBeTruthy()
    expect(screen.getByRole("button", { name: "Edit parts" })).toBeTruthy()
  })
  it("hides money from someone who cannot see prices", async () => {
    get.mockImplementation(() => ok(bundle({ price: null, partsTotal: null, partsCost: null, saving: null, components: bundle().components.map(c => ({ ...c, unitPrice: null, lineTotal: null })) })))
    wrap(<PackageTab itemId="p1" canWrite={false} onChanged={() => undefined} />)
    expect(await screen.findByText("Tap")).toBeTruthy()
    expect(screen.queryByText(/Customers save/)).toBeNull()
    expect(screen.queryByRole("button", { name: "Edit parts" })).toBeNull()
  })
  it("edits quantities and saves the whole list", async () => {
    get.mockImplementation(() => ok(bundle()))
    wrap(<PackageTab itemId="p1" canWrite onChanged={() => undefined} />)
    fireEvent.click(await screen.findByRole("button", { name: "Edit parts" }))
    fireEvent.change(screen.getByLabelText("Quantity of Tap"), { target: { value: "3" } })
    fireEvent.click(screen.getByRole("button", { name: "Remove Pipe" }))
    fireEvent.click(screen.getByRole("button", { name: "Save parts" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/catalogue/items/p1/bundle", { components: [{ itemId: "t1", quantity: 3 }] }))
  })
  it("will not save a bad list", async () => {
    get.mockImplementation(() => ok(bundle()))
    wrap(<PackageTab itemId="p1" canWrite onChanged={() => undefined} />)
    fireEvent.click(await screen.findByRole("button", { name: "Edit parts" }))
    fireEvent.change(screen.getByLabelText("Quantity of Tap"), { target: { value: "0" } })
    expect(screen.getByRole("alert").textContent).toContain("more than zero")
    expect((screen.getByRole("button", { name: "Save parts" }) as HTMLButtonElement).disabled).toBe(true)
  })
})

describe("UsedInList", () => {
  it("names the packages that hold the item, and shows nothing when none do", async () => {
    get.mockImplementation(() => ok([{ bundleId: "p1", name: "Bathroom kit", sku: "BK", status: "ACTIVE", quantity: 2 }]))
    wrap(<UsedInList itemId="t1" />)
    expect(await screen.findByText(/Bathroom kit \(2 each\)/)).toBeTruthy()
    cleanup()
    get.mockImplementation(() => ok([]))
    const { container } = wrap(<UsedInList itemId="t1" />)
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })
})
