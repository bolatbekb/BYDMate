// Render HUD icon SVGs to PNG at 1x..4x and build preview sheets.
// Usage: node render_hud.js <out_dir> [preview_only]
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const OUT = process.argv[2];
const PREVIEW_ONLY = process.argv[3] === 'preview';
const W = 38, H = 41;
const svgDir = path.join(OUT, 'svg');
const hexOf = f => parseInt(f.replace('.svg', ''), 16);
const files = fs.readdirSync(svgDir).filter(f => f.endsWith('.svg')).sort((a, b) => hexOf(a) - hexOf(b));
const scaled = (svg, s) => svg.replace(`width="${W}" height="${H}"`, `width="${W * s}" height="${H * s}"`);

(async () => {
  const browser = await chromium.launch();
  if (!PREVIEW_ONLY) {
    for (const s of [1, 2, 3, 4]) {
      const dir = path.join(OUT, 'png', s === 1 ? `${W}x${H}` : `${W * s}x${H * s}`);
      fs.mkdirSync(dir, { recursive: true });
      const page = await browser.newPage({ viewport: { width: W * s, height: H * s }, deviceScaleFactor: 1 });
      for (const f of files) {
        const svg = scaled(fs.readFileSync(path.join(svgDir, f), 'utf8'), s);
        await page.setContent(`<html><body style="margin:0;background:transparent">${svg}</body></html>`);
        await page.screenshot({ path: path.join(dir, f.replace('.svg', '.png')), omitBackground: true,
          clip: { x: 0, y: 0, width: W * s, height: H * s } });
      }
      await page.close();
    }
  }
  // Preview: each icon at 3x on black + actual size
  const cells = files.map(f => {
    const svg = fs.readFileSync(path.join(svgDir, f), 'utf8');
    const uri = 'data:image/svg+xml;base64,' + Buffer.from(svg).toString('base64');
    return `<div class="c"><img src="${uri}" width="${W * 3}" height="${H * 3}"><img src="${uri}" width="${W}" height="${H}"><div class="n">${f.replace('.svg', '')}</div></div>`;
  }).join('');
  const html = `<html><body style="margin:0;background:#15181d;font:13px ui-monospace,monospace;color:#c9d1d9">
  <style>.g{display:grid;grid-template-columns:repeat(8,1fr);gap:8px;padding:12px;width:${8 * 150}px}
  .c{background:#000;border-radius:10px;padding:10px 6px 6px;display:flex;flex-direction:column;align-items:center;gap:6px}
  img{display:block}.n{color:#8b949e}</style><div class="g">${cells}</div></body></html>`;
  const page = await browser.newPage({ viewport: { width: 8 * 150 + 80, height: 400 }, deviceScaleFactor: 1 });
  await page.setContent(html);
  await page.locator('.g').screenshot({ path: path.join(OUT, 'preview.png') });
  await browser.close();
})();
