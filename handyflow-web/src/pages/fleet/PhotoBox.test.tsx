import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import PhotoBox from "./PhotoBox"
import { photoProblem } from "./photo.logic"

const get = vi.fn(), post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
const show = (canEdit: boolean) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><PhotoBox base="/api/v1/fleet/vehicles/v1" canEdit={canEdit} name="ABC123MP" /></QueryClientProvider>)
beforeEach(() => { get.mockReset(); post.mockReset() })
afterEach(cleanup)

describe("photo rules", () => {
  it("accepts a small image and says what is wrong otherwise", () => {
    expect(photoProblem({ type: "image/png", size: 1000 })).toBeNull()
    expect(photoProblem(null)).toBe("Choose a picture."); expect(photoProblem({ type: "application/pdf", size: 10 })).toContain("JPEG, PNG or WebP")
    expect(photoProblem({ type: "image/jpeg", size: 6 * 1024 * 1024 })).toContain("over 5 MB")
  })
})
describe("PhotoBox", () => {
  it("shows the stored picture", async () => {
    get.mockImplementation((url: string) => url.includes("/download") ? Promise.resolve({ data: new Blob(["x"], { type: "image/png" }) }) : Promise.resolve({ data: { data: [{ id: "d1" }] } })); show(false)
    expect((await screen.findByAltText("Photo of ABC123MP")).getAttribute("src")).toMatch(/^data:image\/png;base64,/)
    expect(get).toHaveBeenCalledWith("/api/v1/fleet/vehicles/v1/documents/d1/download", { responseType: "blob" }); expect(screen.queryByRole("button")).toBeNull()
  })
  it("offers Add photo when there is none, and uploads the chosen picture as a PHOTO", async () => {
    get.mockResolvedValue({ data: { data: [] } }); post.mockResolvedValue({ data: { data: {} } }); show(true)
    fireEvent.click(await screen.findByRole("button", { name: "Add photo" }))
    fireEvent.change(screen.getByLabelText("Photo file for ABC123MP"), { target: { files: [new File(["x"], "car.png", { type: "image/png" })] } })
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, body] = post.mock.calls[0] as [string, FormData]
    expect(url).toBe("/api/v1/fleet/vehicles/v1/documents"); expect(body.get("type")).toBe("PHOTO"); expect((body.get("file") as File).name).toBe("car.png")
  })
  it("refuses a PDF without calling the server", async () => {
    get.mockResolvedValue({ data: { data: [] } }); show(true)
    await screen.findByRole("button", { name: "Add photo" })
    fireEvent.change(screen.getByLabelText("Photo file for ABC123MP"), { target: { files: [new File(["x"], "a.pdf", { type: "application/pdf" })] } })
    expect((await screen.findByRole("alert")).textContent).toContain("JPEG, PNG or WebP"); expect(post).not.toHaveBeenCalled()
  })
})
