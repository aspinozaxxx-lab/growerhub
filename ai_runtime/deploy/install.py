#!/usr/bin/python3
"""Ustanovka iz proverennogo paketa. Sekrety ne vyvodyatsya i ne perezapisyvayutsya."""
import datetime
import grp
import hashlib
import os
import pathlib
import pwd
import secrets
import shutil
import subprocess
import sys
import tarfile
import urllib.request

ROOT = pathlib.Path('/opt/growerhub-ai')
NODE = '24.22.0'
CODEX = '0.163.0'


def run(*args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, text=True, **kwargs)


def private(path, data, group=None):
    if path.exists():
        return
    path.write_text(data)
    path.chmod(0o640 if group else 0o600)
    os.chown(path, 0, grp.getgrnam(group).gr_gid if group else 0)


def maintenance():
    for path in ['/var/lib/growerhub-codex-update', '/opt/growerhub-codex-releases', '/root/growerhub-ai-backups']:
        pathlib.Path(path).mkdir(mode=0o700 if '/var/' in path or '/root/' in path else 0o755, exist_ok=True)
    private(pathlib.Path('/etc/growerhub-ai/maintenance.env'), 'GH_CODEX_UPDATE_TOKEN=' + secrets.token_urlsafe(48) + '\n')
    units = ['growerhub-codex-maintenance.service', 'growerhub-codex-update.service']
    for name in units:
        shutil.copyfile(ROOT / 'deploy' / name, pathlib.Path('/etc/systemd/system') / name)
    run('systemd-analyze', 'verify', *(str(pathlib.Path('/etc/systemd/system') / name) for name in units))
    run('systemctl', 'daemon-reload')
    run('systemctl', 'enable', *units)
    run('systemctl', 'restart', units[0])
    print('Private maintenance API installed')


if os.getuid() != 0 or not (ROOT / 'src/server.mjs').is_file():
    raise SystemExit('Run as root from the deployed runtime')
if '--maintenance-only' in sys.argv:
    maintenance()
    raise SystemExit(0)
for name, home in [('gh-ai-owner', '/var/lib/growerhub-ai-owner'), ('gh-ai-api', '/var/lib/growerhub-ai-public')]:
    try:
        pwd.getpwnam(name)
    except KeyError:
        run('useradd', '--system', '--create-home', '--home-dir', home, '--shell', '/usr/sbin/nologin', name)
    pathlib.Path(home).chmod(0o700)

node = pathlib.Path('/opt/node-v' + NODE + '-linux-x64')
if not node.exists():
    filename = node.name + '.tar.xz'
    base = 'https://nodejs.org/dist/v' + NODE + '/'
    checksums = urllib.request.urlopen(base + 'SHASUMS256.txt', timeout=30).read().decode()
    checksum = next(line.split()[0] for line in checksums.splitlines() if line.split()[-1] == filename)
    archive = pathlib.Path('/opt/' + filename)
    with urllib.request.urlopen(base + filename, timeout=60) as response, archive.open('wb') as output:
        shutil.copyfileobj(response, output)
    if hashlib.sha256(archive.read_bytes()).hexdigest() != checksum:
        raise SystemExit('Node checksum mismatch')
    with tarfile.open(archive) as source:
        source.extractall('/opt', filter='data')
    archive.unlink()
link = pathlib.Path('/opt/growerhub-node')
if link.is_symlink() and link.resolve() != node:
    link.unlink()
if not link.exists():
    link.symlink_to(node, target_is_directory=True)
env = dict(os.environ, PATH=str(link / 'bin') + ':' + os.environ['PATH'])
codex_root = pathlib.Path('/opt/growerhub-codex')
codex_root.mkdir(exist_ok=True)
codex = codex_root / 'node_modules/.bin/codex'
if not codex.exists() or CODEX not in run(str(codex), '--version', env=env).stdout:
    run(str(link / 'bin/npm'), 'install', '--prefix', str(codex_root), '--ignore-scripts', '--no-audit', '--no-fund', '@openai/codex@' + CODEX, env=env)

# Rezervnaya kopiya sozdana do ostanovki starogo bota; rollback ne trebuet ego istorii v novom servise.
backup_root = pathlib.Path('/root/growerhub-ai-backups')
backup_root.mkdir(mode=0o700, exist_ok=True)
marker = backup_root / 'secretary-backup-complete'
if not marker.exists():
    backup = backup_root / ('secretary-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '.tar.gz')
    targets = [p for p in ['/opt/secretary-bot', '/etc/systemd/system/secretary-bot.service', '/root/.codex/auth.json'] if pathlib.Path(p).exists()]
    with tarfile.open(backup, 'w:gz') as archive:
        for target in targets:
            archive.add(target, arcname=target.lstrip('/'))
    backup.chmod(0o600)
    marker.write_text(backup.name)
    marker.chmod(0o600)
if pathlib.Path('/etc/systemd/system/secretary-bot.service').exists():
    run('systemctl', 'disable', '--now', 'secretary-bot.service')
# Vtoraya kopiya uzhe ostanovlennogo bota sohranyaet soglasovannye SQLite i ocheredi.
consistent = backup_root / 'secretary-stopped.tar.gz'
if not consistent.exists():
    with tarfile.open(consistent, 'w:gz') as archive:
        for target in ['/opt/secretary-bot', '/etc/systemd/system/secretary-bot.service', '/root/.codex/auth.json']:
            if pathlib.Path(target).exists():
                archive.add(target, arcname=target.lstrip('/'))
    consistent.chmod(0o600)

owner = pwd.getpwnam('gh-ai-owner')
home = pathlib.Path(owner.pw_dir)
codex_home = home / '.codex'
codex_home.mkdir(mode=0o700, exist_ok=True)
os.chown(codex_home, owner.pw_uid, owner.pw_gid)
auth = codex_home / 'auth.json'
if not auth.exists():
    shutil.copyfile('/root/.codex/auth.json', auth)
    auth.chmod(0o600)
    os.chown(auth, owner.pw_uid, owner.pw_gid)
shutil.copyfile(ROOT / 'deploy/codex.toml', codex_home / 'config.toml')
(codex_home / 'config.toml').chmod(0o444)
workspace = home / 'workspace'
workspace.mkdir(mode=0o555, exist_ok=True)
config = pathlib.Path('/etc/growerhub-ai')
config.mkdir(mode=0o750, exist_ok=True)
private(config / 'owner.env', '\n'.join([
    'GH_AI_MODE=owner', 'GH_AI_TOKEN=' + secrets.token_urlsafe(48),
    'GH_CODEX_BINARY=' + str(codex), 'GH_CODEX_WORKSPACE=' + str(workspace),
    'CODEX_HOME=' + str(codex_home), 'HOME=' + str(home), 'PATH=' + env['PATH'], '',
]))
private(config / 'public.env', '\n'.join([
    'GH_AI_MODE=public', 'GH_AI_PUBLIC_ENABLED=false', 'GH_AI_TOKEN=' + secrets.token_urlsafe(48), '',
]))
for name in ['growerhub-ai-owner.service', 'growerhub-ai-public.service']:
    shutil.copyfile(ROOT / 'deploy' / name, pathlib.Path('/etc/systemd/system') / name)
run('systemd-analyze', 'verify', '/etc/systemd/system/growerhub-ai-owner.service', '/etc/systemd/system/growerhub-ai-public.service')
run('systemctl', 'daemon-reload')
for name in ['growerhub-ai-owner.service', 'growerhub-ai-public.service']:
    run('systemctl', 'enable', name)
    run('systemctl', 'restart', name)
print('Runtime installed; public generation remains disabled')
print(run(str(link / 'bin/node'), '--version').stdout.strip())
print(run(str(codex), '--version', env=env).stdout.strip())
maintenance()
