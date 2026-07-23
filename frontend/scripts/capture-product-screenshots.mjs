import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const outputDir = path.join(rootDir, 'public', 'screenshots');
const baseUrl = process.env.GROWERHUB_SCREENSHOT_BASE_URL || 'http://127.0.0.1:4173';
const pages = [
  {
    locale: 'ru',
    url: process.env.GROWERHUB_SCREENSHOT_URL
      || `${baseUrl}/avtomatizatsiya-mini-fermy/?capture=screenshots`,
    output: outputDir,
  },
  {
    locale: 'en',
    url: `${baseUrl}/en/farm-automation/?capture=screenshots`,
    output: path.join(outputDir, 'en'),
  },
];

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

const browser = await chromium.launch({ executablePath, headless: true });
try {
  const names = ['zones', 'history', 'connection', 'automation'];
  for (const target of pages) {
    fs.mkdirSync(target.output, { recursive: true });
    const page = await browser.newPage({
      viewport: { width: 1440, height: 1000 },
      deviceScaleFactor: 2,
    });
    await page.goto(target.url, { waitUntil: 'networkidle' });
    await page.evaluate(() => document.fonts.ready);

    const demos = page.locator('.product-demo');
    const count = await demos.count();
    if (count !== names.length) {
      throw new Error(
        `Ozhidalos' ${names.length} demo-ekrana dlja ${target.locale}, polucheno ${count}.`,
      );
    }

    for (let index = 0; index < names.length; index += 1) {
      await demos.nth(index).screenshot({
        path: path.join(target.output, `${names[index]}.png`),
        animations: 'disabled',
      });
    }
    await page.close();
  }
} finally {
  await browser.close();
}

console.log(`RU and EN product screenshots written to ${outputDir}`);
