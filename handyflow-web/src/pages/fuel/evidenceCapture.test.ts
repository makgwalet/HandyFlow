import { describe, it, expect, vi, afterEach } from "vitest"
import { fitWithin, dataUrlToBlob, uploadForm, validateUpload, sizeText, shrinkImage, MAX_UPLOAD_BYTES } from "./evidenceCapture"

describe("evidenceCapture", () => {
  it("shrinks the longest side to the limit, keeps the shape and never enlarges", () => {
    expect(fitWithin(4000, 3000, 1280)).toEqual({ width: 1280, height: 960 })
    expect(fitWithin(3000, 4000, 1280)).toEqual({ width: 960, height: 1280 })
    expect(fitWithin(800, 600, 1280)).toEqual({ width: 800, height: 600 })
  })
  it("turns a data URL back into a blob of the same type and size", async () => {
    const b = dataUrlToBlob("data:image/png;base64,aGVsbG8=")
    expect(b.type).toBe("image/png"); expect(b.size).toBe(5)
  })
  it("builds the upload with the plain fields and the file, leaving out empty fields", () => {
    const f = uploadForm({ kind: "PHOTO", clientRequestId: "r1", note: null, x: "" }, { dataUrl: "data:image/jpeg;base64,aGk=", name: "a.jpg" })
    expect(f.get("kind")).toBe("PHOTO"); expect(f.get("clientRequestId")).toBe("r1")
    expect(f.has("note")).toBe(false); expect(f.has("x")).toBe(false)
    expect((f.get("file") as File).name).toBe("a.jpg")
  })
  it("checks type and size before sending", () => {
    expect(validateUpload("PHOTO", null)).toMatch(/Choose/)
    expect(validateUpload("PHOTO", { type: "image/jpeg", size: 0 })).toMatch(/empty/)
    expect(validateUpload("PHOTO", { type: "image/jpeg", size: MAX_UPLOAD_BYTES + 1 })).toMatch(/too large/)
    expect(validateUpload("PHOTO", { type: "application/pdf", size: 10 })).toMatch(/JPEG, PNG or WebP/)
    expect(validateUpload("DOCUMENT", { type: "application/pdf", size: 10 })).toBeNull()
    expect(validateUpload("SIGNATURE", { type: "image/png", size: 10 })).toBeNull()
    expect(validateUpload("PHOTO", { type: "text/html", size: 10 })).not.toBeNull()
  })
  it("writes sizes for people", () => { expect(sizeText(null)).toBe(""); expect(sizeText(100)).toBe("1 KB"); expect(sizeText(2048)).toBe("2 KB"); expect(sizeText(3 * 1024 * 1024)).toBe("3.0 MB") })

  describe("shrinkImage", () => {
    afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks() })
    it("draws the photo smaller on a white canvas and returns a .jpg", async () => {
      const close = vi.fn()
      vi.stubGlobal("createImageBitmap", vi.fn(async () => ({ width: 4000, height: 3000, close })))
      const ctx = { fillRect: vi.fn(), drawImage: vi.fn(), fillStyle: "" }
      const canvas = { width: 0, height: 0, getContext: () => ctx, toDataURL: vi.fn(() => "data:image/jpeg;base64,xx") }
      vi.spyOn(document, "createElement").mockReturnValue(canvas as any)
      const out = await shrinkImage(new Blob(["x"]), "IMG_1.HEIC")
      expect(canvas.width).toBe(1280); expect(canvas.height).toBe(960)
      expect(ctx.drawImage).toHaveBeenCalled(); expect(close).toHaveBeenCalled()
      expect(out).toEqual({ dataUrl: "data:image/jpeg;base64,xx", name: "IMG_1.jpg" })
    })
    it("rejects a file the browser cannot read as an image", async () => {
      vi.stubGlobal("createImageBitmap", vi.fn(async () => { throw new Error("bad") }))
      await expect(shrinkImage(new Blob(["x"]))).rejects.toThrow()
    })
  })
})
