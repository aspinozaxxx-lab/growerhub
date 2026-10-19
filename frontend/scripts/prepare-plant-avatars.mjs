import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sizes = [96, 192, 384, 640];
const target = path.join(root, 'public/assets/plant-avatars/v1');
const manifestPath = path.join(root, 'src/domain/plants/avatarAssets.generated.json');
const manifest = JSON.parse(await fs.readFile(manifestPath, 'utf8').catch(() => '{}'));
const catalog = JSON.parse(await fs.readFile(path.join(root, '../backend/src/main/resources/plant/avatars.json'), 'utf8'));
const sourceArg = process.argv.indexOf('--sources');
const sources = sourceArg < 0 ? {} : JSON.parse(await fs.readFile(process.argv[sourceArg + 1], 'utf8'));
const originals = path.join(root, 'src/domain/plants/avatars');
const inputs = [];
for (const dir of await fs.readdir(originals, { withFileTypes: true })) {
  if (!dir.isDirectory()) continue;
  for (const file of await fs.readdir(path.join(originals, dir.name))) {
    if (file.endsWith('.png')) inputs.push([`${dir.name}/${file.slice(0, -4)}`, path.join(originals, dir.name, file)]);
  }
}
for (const item of catalog) {
  for (const stage of item.stages) {
    const key = `${item.id}/${stage.id}`;
    if (sources[key]) inputs.push([`selected/${key}`, sources[key]]);
    else if (!manifest[`selected/${key}`] && !process.argv.includes('--partial')) throw new Error(`Missing source: ${key}`);
  }
}
for (const [key, input] of inputs) {
  const meta = await sharp(input).metadata();
  if (!meta.hasAlpha) throw new Error(`Missing alpha: ${key}`);
  const images = [];
  for (const width of sizes) {
    const relative = `${key}-${width}.webp`;
    const output = path.join(target, relative);
    await fs.mkdir(path.dirname(output), { recursive: true });
    const info = await sharp(input).rotate().resize({ width, withoutEnlargement: true })
      .webp({ quality: 82, alphaQuality: 95, effort: 6 }).toFile(output);
    images.push({ src: `/assets/plant-avatars/v1/${relative}`, width: info.width, height: info.height });
  }
  manifest[key] = images;
}
await fs.writeFile(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
console.log(`Prepared ${inputs.length} avatars; ${Object.keys(manifest).length} manifest entries`);
