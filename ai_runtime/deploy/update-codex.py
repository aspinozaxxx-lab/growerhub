#!/usr/bin/python3
"""Ruchnoe obnovlenie zakreplennoy versii s proverkoj i otkatom."""
import argparse
import datetime
import json
import os
import pathlib
import re
import shutil
import subprocess
import time
import urllib.request

ENV = pathlib.Path('/etc/growerhub-ai/owner.env')
ROOT = pathlib.Path('/opt/growerhub-ai')
NODE = '/opt/growerhub-node/bin'


def run(*args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, text=True, **kwargs)


def settings():
    return dict(line.split('=', 1) for line in ENV.read_text().splitlines() if line and not line.startswith('#'))


def request(path, value=None):
    data = settings()
    req = urllib.request.Request('http://127.0.0.1:8787/v1/' + path,
        headers={'Authorization': 'Bearer ' + data['GH_AI_TOKEN'], 'Content-Type': 'application/json'},
        data=None if value is None else json.dumps(value).encode())
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response)


def probe(binary):
    data = settings()
    code = """import { CodexProvider } from './src/codex.mjs';
const p = new CodexProvider({binary:process.env.GH_CODEX_BINARY,cwd:process.env.GH_CODEX_WORKSPACE});
const models = await p.models();
if (!models.length || models.some(m=>!m.id || !m.efforts.length)) process.exit(1);
console.log(JSON.stringify({models:models.length}));"""
    env = dict(os.environ, **data)
    env['GH_CODEX_BINARY'] = str(binary)
    return run('runuser', '-u', 'gh-ai-owner', '--', NODE + '/node', '--input-type=module', '-e', code,
               env=env, cwd=ROOT, timeout=45)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('version', nargs='?')
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    if os.getuid() != 0:
        raise SystemExit('Run as root on the AI server')
    if args.check:
        print(json.dumps(request('runtime')))
        return
    if not re.fullmatch(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)', args.version or ''):
        raise SystemExit('Specify an exact stable version, e.g. 0.163.0')
    stage = pathlib.Path('/opt/growerhub-codex-releases') / args.version
    stage.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, PATH=NODE + ':' + os.environ['PATH'])
    run(NODE + '/npm', 'install', '--prefix', str(stage), '--ignore-scripts', '--no-audit', '--no-fund',
        '--registry=https://registry.npmjs.org', '@openai/codex@' + args.version, env=env, timeout=300)
    binary = stage / 'node_modules/.bin/codex'
    if run(str(binary), '--version', env=env, timeout=10).stdout.strip() != 'codex-cli ' + args.version:
        raise SystemExit('Version verification failed; running service unchanged')
    run(NODE + '/node', '--test', cwd=ROOT, env=env, timeout=120)
    probe(binary)
    backup = pathlib.Path('/root/growerhub-ai-backups')
    backup.mkdir(mode=0o700, exist_ok=True)
    previous = backup / ('owner-before-codex-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '.env')
    shutil.copyfile(ENV, previous)
    previous.chmod(0o600)
    changed = False
    stopped = False
    try:
        request('drain', {'enabled': True})
        deadline = time.monotonic() + 60
        while request('health')['active']:
            if time.monotonic() >= deadline:
                raise RuntimeError('Active conversation: retry after it finishes')
            time.sleep(2)
        run('systemctl', 'stop', 'growerhub-ai-owner.service')
        stopped = True
        original = ENV.read_text()
        updated, count = re.subn(r'^GH_CODEX_BINARY=.*$', 'GH_CODEX_BINARY=' + str(binary), original, flags=re.M)
        if count != 1:
            raise RuntimeError('Invalid owner configuration')
        changed = True
        ENV.write_text(updated)
        run('systemctl', 'start', 'growerhub-ai-owner.service')
        for attempt in range(15):
            try:
                if request('health')['ready'] and request('models')['models']:
                    print('Codex updated to ' + args.version + '; previous environment: ' + str(previous))
                    return
            except Exception:
                pass
            time.sleep(1)
        raise RuntimeError('New runtime health check failed')
    except Exception:
        if changed:
            shutil.copyfile(previous, ENV)
        if stopped:
            run('systemctl', 'restart', 'growerhub-ai-owner.service')
        raise
    finally:
        try:
            request('drain', {'enabled': False})
        except Exception:
            pass


if __name__ == '__main__':
    main()
