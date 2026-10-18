"""Proverki ustojchivosti operatora bez ustanovki paketov i realnyh vyzovov modeli."""
import concurrent.futures
import contextlib
import importlib.util
import json
import pathlib
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
import uuid
from unittest.mock import patch, Mock

spec = importlib.util.spec_from_file_location('update_codex', pathlib.Path(__file__).parents[1] / 'deploy/update-codex.py')
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


class SmokeContract(unittest.TestCase):
    def test_probe_uses_runtime_message_contract_and_checks_completion(self):
        models = {'models': [{'id': 'test-model', 'efforts': ['low'], 'defaultEffort': 'low'}]}
        with patch.object(m, 'request', return_value=models), patch.object(m, 'settings', return_value={'GH_AI_TOKEN': 'test'}), patch.object(m.urllib.request, 'urlopen') as open_request:
            response = open_request.return_value.__enter__.return_value
            response.read.return_value = b'{"type":"completed","status":"completed","text":"OK"}\n'
            m.smoke()
            body = json.loads(open_request.call_args.args[0].data)
            self.assertEqual(body['messages'][0]['content'][0]['type'], 'text')
            self.assertEqual(body['tools'], [])
            response.read.return_value = b'{"type":"completed","status":"failed","text":"OK"}\n'
            with self.assertRaises(RuntimeError):
                m.smoke()


class Updates(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = pathlib.Path(self.tmp.name)
        for name, path in [('STATE', root / 'state'), ('ENV', root / 'owner.env'), ('BACKUPS', root / 'backups'), ('RELEASES', root / 'releases')]:
            p = patch.object(m, name, path); p.start(); self.addCleanup(p.stop)
        m.ENV.write_text('GH_CODEX_BINARY=/old/codex\nGH_AI_TOKEN=not-a-real-token\n')
        self.original = m.ENV.read_text()
        for name, value in [('version', '0.163.0'), ('latest', '0.164.0'), ('run', Mock(stdout='')),
                            ('request', {'active': 0}), ('probe', None), ('healthy', True), ('smoke', None)]:
            p = patch.object(m, name, return_value=value); setattr(self, name, p.start()); self.addCleanup(p.stop)
        if __import__('os').name != 'posix':
            guard = threading.RLock()
            @contextlib.contextmanager
            def local_lock(name):
                with guard:
                    yield
            p = patch.object(m, 'lock', local_lock); p.start(); self.addCleanup(p.stop)

    def command(self):
        return {'id': str(uuid.uuid4()), 'target': '0.164.0', 'expectedInstalled': '0.163.0'}

    def job(self):
        command = self.command()
        m.start(command)
        self.version.side_effect = lambda binary=None: '0.164.0' if binary else '0.163.0'
        return m.load()

    def test_retries_are_idempotent_and_versions_are_bound(self):
        value = self.command()
        first = m.start(value)
        self.assertEqual(first, m.start(value))
        self.run.assert_called_once()
        with self.assertRaises(ValueError):
            m.start({**value, 'target': '0.165.0'})
        with self.assertRaises(ValueError):
            m.start({**self.command(), 'target': '0.164.0; touch /tmp/x'})

    def test_only_one_parallel_operation_starts(self):
        def attempt(_):
            try:
                return m.start(self.command())['id']
            except ValueError:
                return None
        with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
            results = list(pool.map(attempt, range(5)))
        self.assertEqual(len([r for r in results if r]), 1)
        self.run.assert_called_once()

    def test_refuses_stale_expected_version_and_unpublished_release(self):
        for changes in [{'expectedInstalled': '0.162.0'}, {'target': '0.165.0'}, {'target': '0.163.0'}, {'target': 'latest'}, {'url': 'http://invalid'}]:
            with self.assertRaises(ValueError):
                m.start({**self.command(), **changes})
        self.run.assert_not_called()

    def test_success_verifies_a_reply_and_keeps_backup(self):
        job = self.job()
        m.perform(job)
        self.assertEqual(m.load()['phase'], 'succeeded')
        self.assertIn('0.164.0', m.ENV.read_text())
        self.smoke.assert_called_once()
        self.assertEqual((m.BACKUPS / ('owner-before-codex-' + job['id'] + '.env')).read_text(), self.original)

    def test_failed_reply_rolls_back_and_checks_previous_reply(self):
        job = self.job()
        self.smoke.side_effect = [RuntimeError('provider failed'), None]
        m.perform(job)
        self.assertEqual(m.load()['phase'], 'rolled_back')
        self.assertEqual(m.load()['installed'], '0.163.0')
        self.assertEqual(m.ENV.read_text(), self.original)
        self.assertEqual(self.smoke.call_count, 2)
        self.healthy.assert_any_call('0.164.0')
        self.healthy.assert_any_call('0.163.0')

    def test_preflight_failure_never_stops_running_service(self):
        job = self.job()
        self.probe.side_effect = RuntimeError('incompatible')
        m.perform(job)
        self.assertEqual(m.load()['phase'], 'failed')
        self.assertEqual(m.ENV.read_text(), self.original)
        self.assertFalse(any(c.args[:2] == ('systemctl', 'stop') for c in self.run.call_args_list))

    def test_busy_conversation_is_not_killed(self):
        job = self.job()
        self.request.return_value = {'active': 1}
        with patch.object(m.time, 'monotonic', side_effect=[0, 61]):
            m.perform(job)
        self.assertEqual(m.load()['phase'], 'failed')
        self.assertEqual(m.ENV.read_text(), self.original)
        self.request.assert_any_call('drain', {'enabled': False})
        self.assertFalse(any(c.args[:2] == ('systemctl', 'stop') for c in self.run.call_args_list))

    def test_restart_recovers_previous_version_before_accepting_new_job(self):
        job = self.job()
        m.atomic(m.BACKUPS / ('owner-before-codex-' + job['id'] + '.env'), self.original)
        m.ENV.write_text('GH_CODEX_BINARY=/new/codex\n')
        m.save(job, 'verifying')
        m.worker()
        self.assertEqual(m.load()['phase'], 'rolled_back')
        self.assertEqual(m.ENV.read_text(), self.original)

    def test_rollback_failure_is_never_reported_as_success(self):
        job = self.job()
        self.healthy.return_value = False
        m.perform(job)
        self.assertEqual(m.load()['phase'], 'rollback_failed')
        self.assertIsNone(m.load()['installed'])

    def test_http_requires_separate_token_and_never_returns_secrets(self):
        token = 'maintenance-only-token-at-least-32-characters'
        server = m.serve(token, ('127.0.0.1', 0))
        thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
        self.addCleanup(server.server_close); self.addCleanup(server.shutdown)
        url = 'http://127.0.0.1:' + str(server.server_port) + '/v1/update'
        with self.assertRaises(urllib.error.HTTPError) as error:
            urllib.request.urlopen(url)
        self.assertEqual(error.exception.code, 401)
        value = self.command()
        req = urllib.request.Request(url, data=json.dumps(value).encode(), headers={'Authorization': 'Bearer ' + token})
        with urllib.request.urlopen(req) as response:
            body = json.load(response)
        self.assertEqual(body['operation']['phase'], 'queued')
        self.assertEqual(set(body['operation']), set(m.PUBLIC))
        self.assertNotIn('GH_AI_TOKEN', json.dumps(body))


if __name__ == '__main__':
    unittest.main()
