import fs from 'node:fs/promises'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'

const SITE_URL = 'https://growerhub.ru'
const MANIFEST_PATH = '/indexnow-manifest.json'
const INDEXNOW_ENDPOINT = 'https://api.indexnow.org/indexnow'

export function validatePublicUrl(value) {
  const url = new URL(value)
  const parts = url.pathname.split('/').filter(Boolean)
  const section = parts[0] === 'en' ? parts[1] : parts[0]
  if (url.origin !== SITE_URL || value !== `${SITE_URL}${url.pathname}` || url.username || url.password
    || url.search || url.hash || !/^\/(?:[a-z0-9-]+\/)*$/.test(url.pathname)
    || ['app', 'api', 'admin', 'auth'].includes(section)) {
    throw new Error('IndexNow accepts canonical public page URLs only')
  }
  return value
}

function validateKey(key) {
  if (!/^[a-zA-Z0-9-]{8,128}$/.test(key ?? '')) {
    throw new Error('INDEXNOW_KEY is missing or invalid')
  }
}

export function parsePublicSitemap(xml) {
  const urls = [...xml.matchAll(/<loc>\s*([^<]+)\s*<\/loc>/g)].map(match => match[1].trim())
  if (!/<urlset[\s>]/.test(xml) || !urls.length || urls.length > 10000
    || new Set(urls).size !== urls.length) {
    throw new Error('Invalid public sitemap')
  }
  return urls.map(validatePublicUrl)
}

export function publicContentHash(html) {
  const content = html
    .replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, tag => (
      /type=["']application\/ld\+json["']/i.test(tag) ? tag : ''
    ))
    .replace(/<link\b[^>]*>/gi, tag => (
      /rel=["'](?:stylesheet|modulepreload)["']/i.test(tag) ? '' : tag
    ))
    .trim()
  return createHash('sha256').update(content).digest('hex')
}

export function changedPublicUrls(current, previous) {
  return [...new Set([...Object.keys(current), ...Object.keys(previous)])]
    .filter(url => current[url] !== previous[url]).sort()
}

const requestOptions = () => ({
  redirect: 'error',
  signal: AbortSignal.timeout(15000),
  headers: { 'User-Agent': 'GrowerHub-IndexNow/1.0' },
})

async function readPreviousPages() {
  const response = await fetch(`${SITE_URL}${MANIFEST_PATH}`, requestOptions())
  if (response.status === 200) {
    const manifest = await response.json()
    if (manifest.version !== 1 || !manifest.pages || typeof manifest.pages !== 'object'
      || Array.isArray(manifest.pages) || !Object.keys(manifest.pages).length) {
      throw new Error('Invalid deployed IndexNow manifest')
    }
    for (const [url, hash] of Object.entries(manifest.pages)) {
      validatePublicUrl(url)
      if (!/^[a-f0-9]{64}$/.test(hash)) throw new Error('Invalid deployed content hash')
    }
    return manifest.pages
  }
  if (response.status !== 404) throw new Error(`Manifest lookup failed: HTTP ${response.status}`)

  const sitemap = await fetch(`${SITE_URL}/sitemap.xml`, requestOptions())
  if (sitemap.status !== 200) throw new Error(`Sitemap lookup failed: HTTP ${sitemap.status}`)
  const urls = parsePublicSitemap(await sitemap.text())
  const pages = {}
  for (let offset = 0; offset < urls.length; offset += 4) {
    const batch = await Promise.all(urls.slice(offset, offset + 4).map(async url => {
      const page = await fetch(url, requestOptions())
      if (page.status === 404 || page.status === 410) return null
      if (page.status !== 200) throw new Error(`Public page lookup failed: HTTP ${page.status}`)
      return [url, publicContentHash(await page.text())]
    }))
    for (const entry of batch) if (entry) pages[entry[0]] = entry[1]
  }
  return pages
}

async function prepare(dist, planFile, key) {
  const urls = parsePublicSitemap(await fs.readFile(path.join(dist, 'sitemap.xml'), 'utf8'))
  await fs.writeFile(path.join(dist, `${key}.txt`), `${key}\n`, 'utf8')
  const pages = {}
  for (const url of urls) {
    const htmlFile = path.join(dist, new URL(url).pathname.slice(1), 'index.html')
    pages[url] = publicContentHash(await fs.readFile(htmlFile, 'utf8'))
  }
  const urlList = changedPublicUrls(pages, await readPreviousPages())
  await fs.writeFile(planFile, `${JSON.stringify({ version: 1, urlList }, null, 2)}\n`)
  await fs.writeFile(path.join(dist, MANIFEST_PATH.slice(1)), `${JSON.stringify({ version: 1, pages }, null, 2)}\n`)
  console.log(`IndexNow: prepared ${urlList.length} changed public URLs`)
}

export async function submitIndexNow(urlList, key, request = fetch) {
  validateKey(key)
  if (!Array.isArray(urlList) || urlList.length > 10000 || new Set(urlList).size !== urlList.length) {
    throw new Error('Invalid IndexNow URL list')
  }
  urlList.forEach(validatePublicUrl)
  if (!urlList.length) return { count: 0, status: 'unchanged' }

  const keyResponse = await request(`${SITE_URL}/${key}.txt`, requestOptions())
  if (keyResponse.status !== 200 || (await keyResponse.text()).trim() !== key) {
    throw new Error('Published IndexNow key verification failed')
  }
  const options = requestOptions()
  const response = await request(INDEXNOW_ENDPOINT, {
    ...options,
    method: 'POST',
    headers: { ...options.headers, 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify({ host: new URL(SITE_URL).hostname, key, urlList }),
  })
  if (![200, 202].includes(response.status)) {
    throw new Error(`IndexNow submission failed: HTTP ${response.status}`)
  }
  return { count: urlList.length, status: response.status }
}

async function main() {
  const key = process.env.INDEXNOW_KEY?.trim()
  validateKey(key)
  const [command, first, second] = process.argv.slice(2)
  if (command === 'prepare' && first && second) {
    await prepare(first, second, key)
  } else if (command === 'submit' && first) {
    const plan = JSON.parse(await fs.readFile(first, 'utf8'))
    if (plan.version !== 1) throw new Error('Invalid IndexNow plan version')
    const result = await submitIndexNow(plan.urlList, key)
    const message = `IndexNow: ${result.count} URLs; status ${result.status}. URL receipt does not confirm indexing.`
    console.log(message)
    if (process.env.GITHUB_STEP_SUMMARY) await fs.appendFile(process.env.GITHUB_STEP_SUMMARY, `${message}\n`)
  } else {
    throw new Error('Usage: indexnow.mjs prepare <dist> <plan> | submit <plan>')
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(error => {
    console.error(error.message)
    process.exitCode = 1
  })
}
