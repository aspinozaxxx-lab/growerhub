#!/usr/bin/env python3
import argparse
import ipaddress
import os
from pathlib import Path
import pwd
import re
import subprocess


def run(*args):
    return subprocess.check_output(args, text=True).strip()


def account():
    try:
        return pwd.getpwnam('gh-ai-tunnel')
    except KeyError:
        run('useradd', '--system', '--create-home', '--home-dir', '/var/lib/growerhub-ai-tunnel', '--shell', '/usr/sbin/nologin', 'gh-ai-tunnel')
        return pwd.getpwnam('gh-ai-tunnel')


parser = argparse.ArgumentParser()
parser.add_argument('mode', choices=['key', 'authorize', 'connect'])
parser.add_argument('--peer')
parser.add_argument('--public-key')
parser.add_argument('--host-key')
args = parser.parse_args()
if os.geteuid() != 0:
    raise SystemExit('Root required')
user = account()
base = Path('/etc/growerhub-ai-tunnel')
base.mkdir(mode=0o700, exist_ok=True)
if args.mode == 'key':
    key = base / 'id_ed25519'
    if not key.exists():
        run('ssh-keygen', '-q', '-t', 'ed25519', '-N', '', '-C', 'growerhub-ai-tunnel', '-f', str(key))
    key.chmod(0o600)
    print(key.with_suffix('.pub').read_text().strip())
else:
    peer = str(ipaddress.ip_address(args.peer))
    if args.mode == 'authorize':
        if not args.public_key or not re.fullmatch(r'ssh-ed25519 [A-Za-z0-9+/=]+(?: [\w-]+)?', args.public_key):
            raise SystemExit('Invalid public key')
        folder = Path(user.pw_dir) / '.ssh'
        folder.mkdir(mode=0o700, exist_ok=True)
        auth = folder / 'authorized_keys'
        auth.write_text(f'from="{peer}",restrict,port-forwarding,permitopen="127.0.0.1:8787",permitopen="127.0.0.1:8788",command="/usr/sbin/nologin" {args.public_key}\n')
        os.chown(folder, user.pw_uid, user.pw_gid)
        os.chown(auth, user.pw_uid, user.pw_gid)
        auth.chmod(0o600)
        policy = Path('/etc/ssh/sshd_config.d/70-growerhub-ai-tunnel.conf')
        previous = policy.read_bytes() if policy.exists() else None
        policy.write_text('''Match User gh-ai-tunnel
    AllowTcpForwarding local
    PermitOpen 127.0.0.1:8787 127.0.0.1:8788
    PermitListen none
    AllowAgentForwarding no
    X11Forwarding no
    PermitTTY no
    ForceCommand /usr/sbin/nologin
Match all
''')
        try:
            run('/usr/sbin/sshd', '-t')
        except subprocess.CalledProcessError:
            if previous is None:
                policy.unlink()
            else:
                policy.write_bytes(previous)
            raise
        run('systemctl', 'reload', 'ssh')
        print('Restricted forwarding key installed')
    else:
        if not args.host_key or not re.fullmatch(r'(ssh-ed25519|ecdsa-sha2-nistp256|ssh-rsa) [A-Za-z0-9+/=]+', args.host_key):
            raise SystemExit('Invalid pinned host key')
        known = base / 'known_hosts'
        known.write_text(f'{peer} {args.host_key}\n')
        known.chmod(0o600)
        unit = Path('/etc/systemd/system/growerhub-ai-tunnel.service')
        unit.write_text(f'''[Unit]
Description=GrowerHub private AI forwarding
After=network-online.target
Wants=network-online.target
[Service]
User=gh-ai-tunnel
LoadCredential=ssh-key:{base}/id_ed25519
LoadCredential=known-hosts:{base}/known_hosts
ExecStart=/usr/bin/ssh -NT -i %d/ssh-key -o UserKnownHostsFile=%d/known-hosts -o StrictHostKeyChecking=yes -o BatchMode=yes -o IdentitiesOnly=yes -o ExitOnForwardFailure=yes -o ServerAliveInterval=20 -o ServerAliveCountMax=3 -o ConnectTimeout=15 -L 127.0.0.1:18787:127.0.0.1:8787 -L 127.0.0.1:18788:127.0.0.1:8788 gh-ai-tunnel@{peer}
Restart=on-failure
RestartSec=10
NoNewPrivileges=yes
PrivateTmp=yes
PrivateDevices=yes
ProtectSystem=strict
ProtectHome=yes
ProtectKernelTunables=yes
ProtectControlGroups=yes
RestrictAddressFamilies=AF_INET AF_INET6 AF_UNIX
CapabilityBoundingSet=
UMask=0077
[Install]
WantedBy=multi-user.target
''')
        run('systemctl', 'daemon-reload')
        run('systemctl', 'enable', '--now', unit.name)
        print(run('systemctl', 'is-active', unit.name))
