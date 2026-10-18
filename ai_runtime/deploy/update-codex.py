#!/usr/bin/python3
"""Obnovlenie Codex: zakreplennaya versiya, ustojchivoe zadanie i proverennyj otkat."""
import argparse
import contextlib
import datetime
import hmac
import json
import os
import pathlib
import re
import shutil
import subprocess
import time
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ENV = pathlib.Path('/etc/growerhub-ai/owner.env')
ROOT = pathlib.Path('/opt/growerhub-ai')
NODE = '/opt/growerhub-node/bin'
STATE = pathlib.Path('/var/lib/growerhub-codex-update')
BACKUPS = pathlib.Path('/root/growerhub-ai-backups')
RELEASES = pathlib.Path('/opt/growerhub-codex-releases')
UNIT = 'growerhub-codex-update.service'
OWNER = 'growerhub-ai-owner.service'
STABLE = re.compile(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)')
TERMINAL = {'succeeded', 'rolled_back', 'failed', 'rollback_failed'}
PUBLIC = ('id', 'target', 'previous', 'installed', 'phase', 'startedAt', 'updatedAt', 'finishedAt', 'error')


def run(*args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, text=True, **kwargs)


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00', 'Z')


def settings():
    return dict(line.split('=', 1) for line in ENV.read_text().splitlines() if line and not line.startswith('#'))


def request(path, value=None):
    req = urllib.request.Request('http://127.0.0.1:8787/v1/' + path,
        headers={'Authorization': 'Bearer ' + settings()['GH_AI_TOKEN'], 'Content-Type': 'application/json'},
        data=None if value is None else json.dumps(value).encode())
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response)


def atomic(path, value):
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.tmp')
    with temporary.open('w') as output:
        os.chmod(temporary, 0o600)
        output.write(value)
        output.flush()
        os.fsync(output.fileno())
    os.replace(temporary, path)
    if hasattr(os, 'O_DIRECTORY'):
        directory = os.open(path.parent, os.O_DIRECTORY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)


@contextlib.contextmanager
def lock(name):
    import fcntl
    STATE.mkdir(mode=0o700, parents=True, exist_ok=True)
    with (STATE / name).open('a') as handle:
        fcntl.flock(handle, fcntl.LOCK_EX)
        yield


def load(job_id=None):
    if job_id is None:
        pointer = STATE / 'current'
        if not pointer.exists():
            return None
        job_id = pointer.read_text().strip()
    job_id = str(uuid.UUID(job_id))
    path = STATE / 'jobs' / (job_id + '.json')
    return json.loads(path.read_text()) if path.exists() else None


def save(job, phase=None, **values):
    job.update(values)
    if phase:
        job['phase'] = phase
    job['updatedAt'] = now()
    if phase in TERMINAL:
        job['finishedAt'] = now()
    atomic(STATE / 'jobs' / (job['id'] + '.json'), json.dumps(job))


def public(job):
    return {key: job.get(key) for key in PUBLIC} if job else None


def version(binary=None):
    output = run(binary or settings()['GH_CODEX_BINARY'], '--version', timeout=10,
                 env=dict(os.environ, PATH=NODE + ':' + os.environ.get('PATH', ''))).stdout.strip()
    if not output.startswith('codex-cli ') or not STABLE.fullmatch(output[10:]):
        raise RuntimeError('invalid_installed_version')
    return output[10:]


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise RuntimeError('registry_redirect')


def latest():
    req = urllib.request.Request('https://registry.npmjs.org/@openai%2fcodex/latest', headers={'Accept': 'application/json'})
    with urllib.request.build_opener(NoRedirect).open(req, timeout=10) as response:
        data = json.loads(response.read(100001))
    if data.get('name') != '@openai/codex' or not STABLE.fullmatch(data.get('version', '')):
        raise RuntimeError('invalid_release')
    return data['version']


def start(value):
    if set(value) != {'id', 'target', 'expectedInstalled'} or not all(isinstance(v, str) for v in value.values()):
        raise ValueError('invalid_request')
    job_id = str(uuid.UUID(value['id']))
    target, expected = value['target'], value['expectedInstalled']
    if not STABLE.fullmatch(target) or not STABLE.fullmatch(expected):
        raise ValueError('invalid_version')
    with lock('state.lock'):
        previous = load(job_id)
        if previous:
            if previous['target'] != target or previous['previous'] != expected:
                raise ValueError('request_changed')
            return public(previous)
        current = load()
        if current and current['phase'] not in TERMINAL:
            raise ValueError('update_running')
        if current and time.time() - datetime.datetime.fromisoformat(current['startedAt'].replace('Z', '+00:00')).timestamp() < 60:
            raise ValueError('retry_later')
        installed = version()
        if installed != expected or tuple(map(int, target.split('.'))) <= tuple(map(int, installed.split('.'))) or target != latest():
            raise ValueError('release_changed')
        job = {'id': job_id, 'target': target, 'previous': installed, 'installed': installed, 'phase': 'queued', 'startedAt': now(), 'error': None}
        save(job)
        atomic(STATE / 'current', job_id)
        try:
            run('systemctl', 'start', '--no-block', UNIT, timeout=10)
        except Exception:
            save(job, 'failed', error='start_failed')
        return public(job)


def probe(binary):
    code = """import { CodexProvider } from './src/codex.mjs';
const p = new CodexProvider({binary:process.env.GH_CODEX_BINARY,cwd:process.env.GH_CODEX_WORKSPACE});
const models = await p.models();
if (!models.length || models.some(m=>!m.id || !m.efforts.length)) process.exit(1);
console.log(JSON.stringify({models:models.length}));"""
    env = dict(os.environ, **settings())
    env['PATH'] = NODE + ':' + os.environ.get('PATH', '')
    env['GH_CODEX_BINARY'] = str(binary)
    return run('runuser', '-u', 'gh-ai-owner', '--', NODE + '/node', '--input-type=module', '-e', code,
               env=env, cwd=ROOT, timeout=45)


def healthy(expected):
    for attempt in range(20):
        try:
            if version() == expected and request('health')['ready'] and request('models')['models']:
                return True
        except Exception:
            pass
        time.sleep(1)
    return False


def smoke():
    models = request('models')['models']
    model = next((m for m in models if m.get('preferred')), models[0])
    body = {'id': str(uuid.uuid4()), 'model': model['id'],
            'effort': 'low' if 'low' in model['efforts'] else model['defaultEffort'],
            'messages': [{'role': 'user', 'content': [{'type': 'text', 'text': 'Reply with exactly OK. This is a connection test. Do not use tools.'}]}],
            'tools': [], 'maxInputTokens': 16000, 'maxOutputTokens': 512, 'maxModelCalls': 1, 'maxToolCalls': 1}
    req = urllib.request.Request('http://127.0.0.1:8787/v1/jobs', data=json.dumps(body).encode(),
        headers={'Authorization': 'Bearer ' + settings()['GH_AI_TOKEN'], 'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=60) as response:
        events = [json.loads(line) for line in response.read(65537).decode().splitlines()]
    if not any(e.get('type') == 'completed' and e.get('status') == 'completed' and e.get('text', '').strip() == 'OK' for e in events):
        raise RuntimeError('smoke_failed')


def rollback(job, code):
    save(job, 'rolling_back', error=code)
    backup = BACKUPS / ('owner-before-codex-' + job['id'] + '.env')
    try:
        if not backup.is_file():
            raise RuntimeError('missing_backup')
        atomic(ENV, backup.read_text())
        run('systemctl', 'restart', OWNER, timeout=30)
        if not healthy(job['previous']):
            raise RuntimeError('rollback_health_failed')
        smoke()
        save(job, 'rolled_back', installed=job['previous'])
    except Exception:
        save(job, 'rollback_failed', error='rollback_health_failed', installed=None)


def perform(job):
    # Posle obryva ne prodolzhaem perekluchenie vslepuyu: vosstanavlivaem proshluyu versiyu.
    if job['phase'] != 'queued':
        if job['phase'] in {'switching', 'verifying', 'rolling_back'}:
            rollback(job, 'interrupted')
        else:
            save(job, 'failed', error='interrupted')
        try:
            request('drain', {'enabled': False})
        except Exception:
            pass
        return
    switched = False
    phase = 'installing'
    try:
        if version() != job['previous'] or latest() != job['target']:
            raise RuntimeError('release_changed')
        save(job, phase)
        stage = RELEASES / job['target']
        stage.mkdir(parents=True, exist_ok=True)
        env = dict(os.environ, PATH=NODE + ':' + os.environ['PATH'], npm_config_cache=str(STATE / 'npm-cache'))
        run(NODE + '/npm', 'install', '--prefix', str(stage), '--ignore-scripts', '--no-audit', '--no-fund',
            '--registry=https://registry.npmjs.org', '@openai/codex@' + job['target'], env=env, timeout=300)
        binary = stage / 'node_modules/.bin/codex'
        phase = 'checking'
        save(job, phase)
        if version(str(binary)) != job['target']:
            raise RuntimeError('version_mismatch')
        run(NODE + '/node', '--test', cwd=ROOT, env=env, timeout=120)
        run('python3', '-m', 'unittest', 'discover', '-s', 'test', '-p', '*_test.py', cwd=ROOT, timeout=60)
        probe(binary)
        BACKUPS.mkdir(mode=0o700, exist_ok=True)
        backup = BACKUPS / ('owner-before-codex-' + job['id'] + '.env')
        atomic(backup, ENV.read_text())
        phase = 'waiting'
        save(job, phase)
        request('drain', {'enabled': True})
        deadline = time.monotonic() + 60
        while request('health')['active']:
            if time.monotonic() >= deadline:
                raise RuntimeError('busy')
            time.sleep(2)
        original = ENV.read_text()
        updated, count = re.subn(r'^GH_CODEX_BINARY=.*$', lambda _: 'GH_CODEX_BINARY=' + str(binary), original, flags=re.M)
        if count != 1:
            raise RuntimeError('invalid_config')
        phase = 'switching'
        save(job, phase)
        switched = True
        run('systemctl', 'stop', OWNER, timeout=30)
        atomic(ENV, updated)
        run('systemctl', 'start', OWNER, timeout=30)
        phase = 'verifying'
        save(job, phase)
        if not healthy(job['target']):
            raise RuntimeError('health_failed')
        smoke()
        save(job, 'succeeded', installed=job['target'], error=None)
    except Exception:
        code = {'installing': 'install_failed', 'checking': 'preflight_failed', 'waiting': 'busy_or_unavailable',
                'switching': 'switch_failed', 'verifying': 'health_failed'}[phase]
        if switched:
            rollback(job, code)
        else:
            save(job, 'failed', error=code)
    finally:
        try:
            request('drain', {'enabled': False})
        except Exception:
            pass


def worker():
    with lock('worker.lock'):
        job = load()
        if job and job['phase'] not in TERMINAL:
            perform(job)


def serve(token, address=('127.0.0.1', 8789)):
    if len(token) < 32:
        raise RuntimeError('Missing maintenance token')

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def answer(self, status, body):
            data = json.dumps(body).encode()
            self.send_response(status)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Cache-Control', 'no-store')
            self.send_header('Content-Length', str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def authorized(self):
            if not hmac.compare_digest(self.headers.get('Authorization', ''), 'Bearer ' + token):
                self.answer(401, {'error': 'unauthorized'})
                return False
            if self.path != '/v1/update':
                self.answer(404, {'error': 'not_found'})
                return False
            return True

        def do_GET(self):
            if self.authorized():
                self.answer(200, {'operation': public(load())})

        def do_POST(self):
            if not self.authorized():
                return
            self.connection.settimeout(10)
            try:
                length = int(self.headers.get('Content-Length', '0'))
                if length < 1 or length > 1024 or self.headers.get('Transfer-Encoding'):
                    raise ValueError('invalid_body')
                value = json.loads(self.rfile.read(length))
                if not isinstance(value, dict):
                    raise ValueError('invalid_body')
                self.answer(202, {'operation': start(value)})
            except (ValueError, TypeError):
                self.answer(409, {'error': 'update_not_accepted'})
            except Exception:
                self.answer(503, {'error': 'maintenance_unavailable'})

    return ThreadingHTTPServer(address, Handler)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('version', nargs='?')
    parser.add_argument('--check', action='store_true')
    parser.add_argument('--serve', action='store_true')
    parser.add_argument('--job', action='store_true')
    args = parser.parse_args()
    if os.getuid() != 0:
        raise SystemExit('Run as root on the AI server')
    if args.check:
        print(json.dumps({'runtime': request('runtime'), 'operation': public(load())}))
    elif args.serve:
        serve(os.environ.get('GH_CODEX_UPDATE_TOKEN', '')).serve_forever()
    elif args.job:
        worker()
    else:
        print(json.dumps(start({'id': str(uuid.uuid4()), 'target': args.version, 'expectedInstalled': version()})))


if __name__ == '__main__':
    main()
