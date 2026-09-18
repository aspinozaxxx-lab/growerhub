// @vitest-environment node
import { describe, expect, it, vi } from 'vitest'
import { changedPublicUrls, parsePublicSitemap, publicContentHash, submitIndexNow, validatePublicUrl } from './indexnow.mjs'

const origin = 'https://growerhub.ru'
const key = 'test-indexnow-key'

describe('IndexNow public publication boundaries', () => {
  it('rejects private, foreign and noncanonical URLs before sending anything', async () => {
    for (const url of [
      `${origin}/app/`, `${origin}/en/app/demo/`, `${origin}/api/`, `${origin}/admin/`,
      `${origin}/auth/`, `${origin}/en/?source=test`, `${origin}/#demo`, `${origin}/en`, `${origin}/?`, `${origin}/#`,
      'https://example.com/', 'https://user:pass@growerhub.ru/',
    ]) {
      const request = vi.fn()
      expect(() => validatePublicUrl(url)).toThrow()
      await expect(submitIndexNow([url], key, request)).rejects.toThrow()
      expect(request).not.toHaveBeenCalled()
    }
    expect(validatePublicUrl(`${origin}/en/articles/watering/`)).toBe(`${origin}/en/articles/watering/`)
  })

  it('selects changed, added and deleted pages but leaves unchanged pages alone', () => {
    expect(changedPublicUrls({ same: 'a', changed: 'new', added: 'c' }, { same: 'a', changed: 'old', deleted: 'd' }))
      .toEqual(['added', 'changed', 'deleted'])
  })

  it('ignores JS and CSS build hashes while keeping content and structured data changes', () => {
    const html = version => `<link rel="stylesheet" href="/${version}.css"><link rel="modulepreload" href="/${version}.js"><script src="/${version}.js"></script><script type="application/json">{"build":"${version}"}</script><h1>Water a plant</h1><script type="application/ld+json">{"name":"Watering"}</script>`
    expect(publicContentHash(html('old'))).toBe(publicContentHash(html('new')))
    expect(publicContentHash(html('old'))).not.toBe(publicContentHash(html('old').replace('Water a plant', 'Choose a plant')))
    expect(publicContentHash(html('old'))).not.toBe(publicContentHash(html('old').replace('Watering', 'Irrigation')))
  })

  it('accepts a public sitemap and rejects fallback HTML or private entries', () => {
    expect(parsePublicSitemap(`<urlset><url><loc>${origin}/en/</loc></url></urlset>`)).toEqual([`${origin}/en/`])
    expect(() => parsePublicSitemap('<html>Not found</html>')).toThrow()
    expect(() => parsePublicSitemap(`<urlset><url><loc>${origin}/app/</loc></url></urlset>`)).toThrow()
  })

  it('makes no request for unchanged pages and no submission without the published key', async () => {
    const request = vi.fn().mockResolvedValue({ status: 404 })
    await expect(submitIndexNow([], key, request)).resolves.toEqual({ count: 0, status: 'unchanged' })
    expect(request).not.toHaveBeenCalled()
    await expect(submitIndexNow([`${origin}/en/`], key, request)).rejects.toThrow('key verification failed')
    expect(request).toHaveBeenCalledTimes(1)
  })

  it.each([200, 202])('sends the exact batch once and preserves HTTP %s', async status => {
    const request = vi.fn()
      .mockResolvedValueOnce({ status: 200, text: async () => `${key}\n` })
      .mockResolvedValueOnce({ status })
    const urlList = [`${origin}/en/`]
    await expect(submitIndexNow(urlList, key, request)).resolves.toEqual({ count: 1, status })
    expect(request).toHaveBeenCalledTimes(2)
    const [url, options] = request.mock.calls[1]
    expect(url).toBe('https://api.indexnow.org/indexnow')
    expect(options.method).toBe('POST')
    expect(JSON.parse(options.body)).toEqual({ host: 'growerhub.ru', key, urlList })
  })

  it('reports rate limits without automatic retries', async () => {
    const request = vi.fn()
      .mockResolvedValueOnce({ status: 200, text: async () => key })
      .mockResolvedValueOnce({ status: 429 })
    await expect(submitIndexNow([`${origin}/en/`], key, request)).rejects.toThrow('HTTP 429')
    expect(request).toHaveBeenCalledTimes(2)
  })
})
