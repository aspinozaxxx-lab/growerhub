import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright-core';
import sharp from 'sharp';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const outputDir = path.join(rootDir, 'public', 'screenshots');
const baseUrl = process.env.GROWERHUB_SCREENSHOT_BASE_URL || 'http://127.0.0.1:4173';
if (!['localhost', '127.0.0.1', '[::1]'].includes(new URL(baseUrl).hostname)) {
  throw new Error('Snimki snimajutsja tolko s lokalnogo stenda s vklyuchennym demo.');
}
const executablePath = [
  process.env.CHROME_PATH,
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  '/usr/bin/google-chrome', '/usr/bin/chromium',
].filter(Boolean).find((candidate) => fs.existsSync(candidate));
if (!executablePath) throw new Error('Ukazhite CHROME_PATH.');

const browser = await chromium.launch({ executablePath, headless: true });
try {
  for (const locale of ['ru', 'en']) {
    const output = locale === 'en' ? path.join(outputDir, 'en') : outputDir;
    fs.mkdirSync(output, { recursive: true });
    const context = await browser.newContext({ viewport: { width: 1280, height: 720 }, deviceScaleFactor: 1 });
    const page = await context.newPage();
    const shot = async (name) => {
      await page.evaluate(() => document.fonts.ready);
      const bytes = await page.screenshot({ animations: 'disabled' });
      await sharp(bytes).webp({ quality: 86 }).toFile(path.join(output, name + '.webp'));
      if (name === 'zones') {
        await sharp(bytes).resize({ width: 640 }).webp({ quality: 84 }).toFile(path.join(output, 'zones-640.webp'));
      }
    };
    const go = async (route) => {
      await page.goto(baseUrl + route);
      await page.locator('.demo-banner').waitFor({ timeout: 60000 });
    };
    await page.goto(baseUrl + '/app/demo/?lang=' + locale);
    await page.locator('.farm-dashboard-room').waitFor({ timeout: 120000 });
    await shot('zones');
    await page.getByRole('button', { name: locale === 'ru' ? 'Открыть статистику: Температура воздуха' : 'Open statistics: Air temperature', exact: true }).first().click();
    await page.getByRole('button', { name: locale === 'ru' ? 'За неделю' : 'Past week', exact: true }).click();
    await page.locator('.recharts-line').first().waitFor();
    await shot('history');
    await go('/app/settings/devices/');
    await page.locator('.device-card').first().waitFor();
    await shot('connection');
    await go('/app/automations/');
    await page.getByRole('button', { name: locale === 'ru' ? 'Сохранить' : 'Save', exact: true }).first().waitFor();
    await shot('automation');
    await go('/app/manual-watering/');
    await page.getByRole('button', { name: locale === 'ru' ? 'Журнал насоса' : 'Pump log', exact: true }).first().click();
    await page.getByRole('region').first().waitFor();
    await page.getByRole('region').first().getByRole('article').first().scrollIntoViewIfNeeded();
    await shot('demo-watering');
    await go('/app/demo-tools/');
    await page.locator('.demo-tool-card select option').first().waitFor({ state: 'attached' });
    await shot('demo-tools');
    await go('/app/');
    await page.locator('.farm-dashboard-room').waitFor();
    await page.getByRole('button', { name: locale === 'ru' ? 'Открыть статистику: Свет' : 'Open statistics: Light', exact: true }).first().click();
    await page.getByRole('region', { name: locale === 'ru' ? 'По дням' : 'By day', exact: true }).waitFor();
    await shot('demo-energy');
    await context.close();
  }
} finally {
  await browser.close();
}
console.log('Snimki obshchego prilozhenija s virtualnymi ustrojstvami obnovleny.');
