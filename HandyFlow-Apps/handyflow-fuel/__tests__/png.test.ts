import { MIN_INK_PIXELS, SIG_HEIGHT, SIG_WIDTH, adler32, blankBitmap, crc32, drawStrokes, encodePng, hasSignature, inkPixels, toBase64, signatureBase64 } from "@driver/png";

const be32 = (b: Uint8Array, o: number) => ((b[o]! << 24) | (b[o + 1]! << 16) | (b[o + 2]! << 8) | b[o + 3]!) >>> 0;

/** Reads chunks back out of a PNG, checking every CRC, so a bad encoder fails here and not at the server. */
function chunks(png: Uint8Array): Record<string, Uint8Array> {
  const out: Record<string, Uint8Array> = {};
  let o = 8;
  while (o < png.length) {
    const len = be32(png, o);
    const type = String.fromCharCode(...png.subarray(o + 4, o + 8));
    const body = png.subarray(o + 8, o + 8 + len);
    expect(be32(png, o + 8 + len)).toBe(crc32(png.subarray(o + 4, o + 8 + len)));
    out[type] = body;
    o += 12 + len;
  }
  return out;
}

/** Undoes the stored-block zlib stream. */
function inflateStored(z: Uint8Array): Uint8Array {
  const parts: number[] = [];
  let o = 2;
  for (;;) {
    const last = z[o]! & 1, len = z[o + 1]! | (z[o + 2]! << 8);
    expect((~len & 0xffff)).toBe(z[o + 3]! | (z[o + 4]! << 8));
    parts.push(...z.subarray(o + 5, o + 5 + len));
    o += 5 + len;
    if (last) break;
  }
  const raw = Uint8Array.from(parts);
  expect(be32(z, o)).toBe(adler32(raw));
  return raw;
}

describe("signature drawing", () => {
  it("is blank until something is drawn, and a tap is not a signature", () => {
    const b = blankBitmap();
    expect(hasSignature(b)).toBe(false);
    drawStrokes([[{ x: 10, y: 10 }]], SIG_WIDTH, SIG_HEIGHT, b);
    expect(inkPixels(b)).toBeGreaterThan(0);
    expect(hasSignature(b)).toBe(false);
  });
  it("a real stroke counts as a signature", () => {
    const b = drawStrokes([[{ x: 20, y: 120 }, { x: 80, y: 30 }, { x: 140, y: 120 }, { x: 200, y: 30 }]], SIG_WIDTH, SIG_HEIGHT);
    expect(inkPixels(b)).toBeGreaterThanOrEqual(MIN_INK_PIXELS);
    expect(hasSignature(b)).toBe(true);
  });
  it("draws a continuous line between two points", () => {
    const b = drawStrokes([[{ x: 10, y: 50 }, { x: 110, y: 50 }]], SIG_WIDTH, SIG_HEIGHT, blankBitmap(), 0);
    for (let x = 10; x <= 110; x++) expect(b.pixels[50 * SIG_WIDTH + x]).toBe(1);
    expect(b.pixels[50 * SIG_WIDTH + 111]).toBe(0);
  });
  it("scales a pad of any size to the bitmap", () => {
    const b = drawStrokes([[{ x: 0, y: 0 }, { x: 199, y: 99 }]], 200, 100, blankBitmap(), 0);
    expect(b.pixels[0]).toBe(1);
    expect(b.pixels[(SIG_HEIGHT - 1) * SIG_WIDTH + (SIG_WIDTH - 2)]).toBe(1);
  });
  it("ignores points outside the pad instead of crashing", () => {
    expect(() => drawStrokes([[{ x: -50, y: -50 }, { x: 9999, y: 9999 }]], SIG_WIDTH, SIG_HEIGHT)).not.toThrow();
  });
});

describe("PNG encoder", () => {
  const b = drawStrokes([[{ x: 20, y: 120 }, { x: 80, y: 30 }, { x: 140, y: 120 }]], SIG_WIDTH, SIG_HEIGHT);
  const png = encodePng(b);

  it("starts with the PNG signature, and the server's sniffing recognises it", () => {
    expect([...png.subarray(0, 8)]).toEqual([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
  });
  it("has a valid header: 400 x 150, 1-bit greyscale", () => {
    const h = chunks(png).IHDR!;
    expect(be32(h, 0)).toBe(SIG_WIDTH);
    expect(be32(h, 4)).toBe(SIG_HEIGHT);
    expect([h[8], h[9]]).toEqual([1, 0]);
  });
  it("has correct CRCs, a valid zlib stream, and pixels that round-trip", () => {
    const raw = inflateStored(chunks(png).IDAT!);
    const rowBytes = SIG_WIDTH / 8;
    expect(raw.length).toBe((rowBytes + 1) * SIG_HEIGHT);
    let ink = 0;
    for (let y = 0; y < SIG_HEIGHT; y++) {
      expect(raw[y * (rowBytes + 1)]).toBe(0); // filter none
      for (let x = 0; x < SIG_WIDTH; x++) {
        const bit = (raw[y * (rowBytes + 1) + 1 + (x >> 3)]! >> (7 - (x & 7))) & 1;
        if (bit === 0) ink += 1;
        expect(bit === 0).toBe(b.pixels[y * SIG_WIDTH + x] === 1);
      }
    }
    expect(ink).toBe(inkPixels(b));
  });
  it("is small enough to queue and upload", () => expect(png.length).toBeLessThan(10_000));
  it("matches the known CRC-32 and Adler-32 check values", () => {
    const text = Uint8Array.from([..."123456789"].map((c) => c.charCodeAt(0)));
    expect(crc32(text)).toBe(0xcbf43926);
    expect(adler32(text)).toBe(0x091e01de);
  });
});

describe("base64", () => {
  it.each([["", ""], ["f", "Zg=="], ["fo", "Zm8="], ["foo", "Zm9v"], ["foob", "Zm9vYg=="], ["fooba", "Zm9vYmE="], ["foobar", "Zm9vYmFy"]])("encodes %j", (plain, enc) => {
    expect(toBase64(Uint8Array.from([...plain].map((c) => c.charCodeAt(0))))).toBe(enc);
  });
  it("the signature starts with the base64 of the PNG header", () => {
    expect(signatureBase64(blankBitmap()).startsWith("iVBORw0KGgo")).toBe(true);
  });
});
