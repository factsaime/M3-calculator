// Рисует build/icon.png (512×512) без внешних библиотек:
// считает форму пиксель за пикселем и кодирует PNG через встроенный zlib.
const fs = require("fs");
const path = require("path");
const zlib = require("zlib");

const SIZE = 512;
const SS = 3; // суперсэмплинг 3×3 для сглаживания
const U = 108; // система координат как в Android-векторе

const BG = [0x8a, 0x4f, 0xc4];
const FG = [0xff, 0xff, 0xff];

// скруглённый прямоугольник в координатах 108×108
const roundRect = (x0, y0, x1, y1, r) => (x, y) => {
  if (x < x0 || x > x1 || y < y0 || y > y1) return false;
  const cx = Math.min(Math.max(x, x0 + r), x1 - r);
  const cy = Math.min(Math.max(y, y0 + r), y1 - r);
  const dx = x - cx;
  const dy = y - cy;
  return dx * dx + dy * dy <= r * r;
};

const plate = roundRect(4, 4, 104, 104, 24);
const bar1 = roundRect(28, 40, 80, 52, 6);
const bar2 = roundRect(28, 58, 80, 70, 6);

const scale = U / SIZE;
const pixels = Buffer.alloc(SIZE * SIZE * 4);

for (let py = 0; py < SIZE; py++) {
  for (let px = 0; px < SIZE; px++) {
    let inPlate = 0;
    let inBar = 0;
    for (let sy = 0; sy < SS; sy++) {
      for (let sx = 0; sx < SS; sx++) {
        const x = (px + (sx + 0.5) / SS) * scale;
        const y = (py + (sy + 0.5) / SS) * scale;
        if (plate(x, y)) inPlate++;
        if (bar1(x, y) || bar2(x, y)) inBar++;
      }
    }
    const total = SS * SS;
    const aPlate = inPlate / total;
    const aBar = inBar / total;

    // белые полоски лежат поверх фиолетовой плашки
    const r = BG[0] * (1 - aBar) + FG[0] * aBar;
    const g = BG[1] * (1 - aBar) + FG[1] * aBar;
    const b = BG[2] * (1 - aBar) + FG[2] * aBar;

    const i = (py * SIZE + px) * 4;
    pixels[i] = Math.round(r);
    pixels[i + 1] = Math.round(g);
    pixels[i + 2] = Math.round(b);
    pixels[i + 3] = Math.round(aPlate * 255);
  }
}

const crcTable = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c;
  }
  return t;
})();

const crc32 = (buf) => {
  let c = 0xffffffff;
  for (let i = 0; i < buf.length; i++) c = crcTable[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ 0xffffffff) >>> 0;
};

const chunk = (type, data) => {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length, 0);
  const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body), 0);
  return Buffer.concat([len, body, crc]);
};

const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(SIZE, 0);
ihdr.writeUInt32BE(SIZE, 4);
ihdr[8] = 8; // глубина 8 бит
ihdr[9] = 6; // RGBA
ihdr[10] = 0;
ihdr[11] = 0;
ihdr[12] = 0;

const raw = Buffer.alloc(SIZE * (SIZE * 4 + 1));
for (let y = 0; y < SIZE; y++) {
  raw[y * (SIZE * 4 + 1)] = 0; // фильтр none
  pixels.copy(raw, y * (SIZE * 4 + 1) + 1, y * SIZE * 4, (y + 1) * SIZE * 4);
}

const png = Buffer.concat([
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  chunk("IHDR", ihdr),
  chunk("IDAT", zlib.deflateSync(raw, { level: 9 })),
  chunk("IEND", Buffer.alloc(0)),
]);

const outDir = path.join(__dirname, "..", "build");
fs.mkdirSync(outDir, { recursive: true });
fs.writeFileSync(path.join(outDir, "icon.png"), png);
console.log(`icon.png: ${SIZE}×${SIZE}, ${png.length} байт`);
