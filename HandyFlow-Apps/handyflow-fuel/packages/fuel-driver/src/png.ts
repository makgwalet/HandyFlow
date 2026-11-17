/**
 * A signature drawn on screen becomes a small black-and-white PNG, with no native drawing library: strokes are drawn into a
 * 1-bit bitmap and written out as an uncompressed PNG (a few KB). The server accepts a PNG as signature evidence.
 */
export interface Point { x: number; y: number }
export type Stroke = Point[];

export const SIG_WIDTH = 400;
export const SIG_HEIGHT = 150;
/** Fewer dark pixels than this is a tap or a scribble, not a signature. */
export const MIN_INK_PIXELS = 60;

export interface Bitmap { width: number; height: number; /** 1 = ink */ pixels: Uint8Array }

export function blankBitmap(width = SIG_WIDTH, height = SIG_HEIGHT): Bitmap {
  return { width, height, pixels: new Uint8Array(width * height) };
}

function stamp(b: Bitmap, cx: number, cy: number, radius: number): void {
  for (let dy = -radius; dy <= radius; dy++) {
    for (let dx = -radius; dx <= radius; dx++) {
      const x = cx + dx, y = cy + dy;
      if (x >= 0 && y >= 0 && x < b.width && y < b.height) b.pixels[y * b.width + x] = 1;
    }
  }
}

/** Draws strokes given in a pad of padWidth x padHeight into the bitmap, scaled to fit. Bresenham lines, 3 px thick. */
export function drawStrokes(strokes: Stroke[], padWidth: number, padHeight: number, b: Bitmap = blankBitmap(), radius = 1): Bitmap {
  const sx = b.width / Math.max(1, padWidth), sy = b.height / Math.max(1, padHeight);
  for (const stroke of strokes) {
    const pts = stroke.map((p) => ({ x: Math.round(p.x * sx), y: Math.round(p.y * sy) }));
    if (pts.length === 1 && pts[0]) stamp(b, pts[0].x, pts[0].y, radius);
    for (let i = 1; i < pts.length; i++) {
      const a = pts[i - 1]!, c = pts[i]!;
      let x = a.x, y = a.y;
      const dx = Math.abs(c.x - a.x), dy = -Math.abs(c.y - a.y);
      const stepX = a.x < c.x ? 1 : -1, stepY = a.y < c.y ? 1 : -1;
      let err = dx + dy;
      for (;;) {
        stamp(b, x, y, radius);
        if (x === c.x && y === c.y) break;
        const e2 = 2 * err;
        if (e2 >= dy) { err += dy; x += stepX; }
        if (e2 <= dx) { err += dx; y += stepY; }
      }
    }
  }
  return b;
}

export const inkPixels = (b: Bitmap): number => b.pixels.reduce((n, v) => n + v, 0);
export const hasSignature = (b: Bitmap): boolean => inkPixels(b) >= MIN_INK_PIXELS;

// ── PNG ─────────────────────────────────────────────────────────────────────

const CRC_TABLE = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();

export function crc32(bytes: Uint8Array): number {
  let c = 0xffffffff;
  for (const b of bytes) c = CRC_TABLE[(c ^ b) & 0xff]! ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
}

export function adler32(bytes: Uint8Array): number {
  let a = 1, b = 0;
  for (const v of bytes) { a = (a + v) % 65521; b = (b + a) % 65521; }
  return ((b << 16) | a) >>> 0;
}

const u32 = (n: number) => [(n >>> 24) & 255, (n >>> 16) & 255, (n >>> 8) & 255, n & 255];

function chunk(type: string, data: Uint8Array): number[] {
  const typeBytes = [...type].map((c) => c.charCodeAt(0));
  const body = new Uint8Array([...typeBytes, ...data]);
  return [...u32(data.length), ...body, ...u32(crc32(body))];
}

/** zlib stream made only of "stored" (uncompressed) blocks: valid, simple, and small enough for a 1-bit image. */
function zlibStored(raw: Uint8Array): number[] {
  const out: number[] = [0x78, 0x01];
  for (let i = 0; i < raw.length || i === 0; i += 65535) {
    const part = raw.subarray(i, Math.min(i + 65535, raw.length));
    const last = i + 65535 >= raw.length ? 1 : 0;
    out.push(last, part.length & 255, part.length >>> 8, ~part.length & 255, (~part.length >>> 8) & 255, ...part);
    if (raw.length === 0) break;
  }
  return [...out, ...u32(adler32(raw))];
}

/** 1-bit greyscale PNG: ink is black, everything else white. */
export function encodePng(b: Bitmap): Uint8Array {
  const rowBytes = Math.ceil(b.width / 8);
  const raw = new Uint8Array((rowBytes + 1) * b.height); // each row starts with filter type 0
  for (let y = 0; y < b.height; y++) {
    const o = y * (rowBytes + 1) + 1;
    raw.fill(0xff, o, o + rowBytes); // white
    for (let x = 0; x < b.width; x++) if (b.pixels[y * b.width + x]) raw[o + (x >> 3)]! &= ~(0x80 >> (x & 7));
  }
  const ihdr = new Uint8Array([...u32(b.width), ...u32(b.height), 1, 0, 0, 0, 0]); // 1-bit, greyscale
  return new Uint8Array([
    0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
    ...chunk("IHDR", ihdr),
    ...chunk("IDAT", new Uint8Array(zlibStored(raw))),
    ...chunk("IEND", new Uint8Array(0)),
  ]);
}

const B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
export function toBase64(bytes: Uint8Array): string {
  let s = "";
  for (let i = 0; i < bytes.length; i += 3) {
    const a = bytes[i]!, b = bytes[i + 1], c = bytes[i + 2];
    const n = (a << 16) | ((b ?? 0) << 8) | (c ?? 0);
    s += B64[(n >> 18) & 63]! + B64[(n >> 12) & 63]! + (b === undefined ? "=" : B64[(n >> 6) & 63]!) + (c === undefined ? "=" : B64[n & 63]!);
  }
  return s;
}

export const signatureBase64 = (b: Bitmap): string => toBase64(encodePng(b));
