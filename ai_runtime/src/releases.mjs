import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const execute = promisify(execFile);
const stable = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;
export function newer(latest, installed) {
  if (!stable.test(latest || '') || !stable.test(installed || '')) return false;
  const a = latest.split('.').map(Number), b = installed.split('.').map(Number);
  for (let i = 0; i < 3; i++) if (a[i] !== b[i]) return a[i] > b[i];
  return false;
}
export class CodexReleases {
  constructor({ binary, fetcher = fetch, version = async () => (await execute(binary, ['--version'], { timeout: 5000, maxBuffer: 4096 })).stdout, now = () => new Date() }) {
    this.version = version; this.fetcher = fetcher; this.now = now;
  }
  async status() {
    let installed = null, latest = null;
    const checkedAt = this.now().toISOString();
    try {
      installed = (await this.version()).trim().match(/^codex-cli (\d+\.\d+\.\d+)$/)?.[1];
      if (!installed) throw new Error('version_unavailable');
      const response = await this.fetcher('https://registry.npmjs.org/@openai%2fcodex/latest', { redirect: 'error', signal: AbortSignal.timeout(10000), headers: { Accept: 'application/json' } });
      if (!response.ok) throw new Error('registry_unavailable');
      const raw = await response.text();
      if (raw.length > 100000) throw new Error('registry_response_too_large');
      const data = JSON.parse(raw);
      if (data.name !== '@openai/codex' || !stable.test(data.version || '')) throw new Error('invalid_release');
      latest = data.version;
      return { installed, latest, checkedAt, updateAvailable: newer(latest, installed), error: null };
    } catch { return { installed, latest, checkedAt, updateAvailable: false, error: 'check_unavailable' }; }
  }
}
