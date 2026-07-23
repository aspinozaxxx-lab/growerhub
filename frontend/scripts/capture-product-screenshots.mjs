import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const outputDir = path.join(rootDir, 'public', 'screenshots');
const pageUrl = process.env.GROWERHUB_SCREENSHOT_URL
  || 'http://127.0.0.1:4173/avtomatizatsiya-mini-fermy/?capture=screenshots';

const executableCandidates = [
  process.env.CHROME_PATH,
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
].filter(Boolean);
const executablePath = executableCandidates.find((candidate) => fs.existsSync(candidate));

if (!executablePath) {
  throw new Error('Chrome/Chromium ne najden. Ukazhite CHROME_PATH.');
}

fs.mkdirSync(outputDir, { recursive: true });

const browser = await chromium.launch({ executablePath, headless: true });
try {
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1000 },
    deviceScaleFactor: 2,
  });
  await page.goto(pageUrl, { waitUntil: 'networkidle' });
  await page.evaluate(() => document.fonts.ready);

  const names = ['zones', 'history', 'connection', 'automation'];
  const demos = page.locator('.product-demo');
  const count = await demos.count();
  if (count !== names.length) {
    throw new Error(`Ozhidalos' ${names.length} demo-ekrana, polucheno ${count}.`);
  }

  for (let index = 0; index < names.length; index += 1) {
    await demos.nth(index).screenshot({
      path: path.join(outputDir, `${names[index]}.png`),
      animations: 'disabled',
    });
  }
} finally {
  await browser.close();
}

console.log(`Product screenshots written to ${outputDir}`);
