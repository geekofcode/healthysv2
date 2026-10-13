import importlib.util
import tempfile
import contextlib
import io
from unittest.mock import patch
import unittest
from pathlib import Path
spec=importlib.util.spec_from_file_location('backup_restore',Path(__file__).parents[1]/'backup_restore.py')
b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
class BackupSafetyTest(unittest.TestCase):
    def test_backup_requires_paused_writers(self):
        with self.assertRaisesRegex(ValueError,'writers-paused'):b.backup(Path('ignored'),False)
    def test_restore_requires_explicit_isolated_target(self):
        with self.assertRaisesRegex(ValueError,'isolated-target-confirmed'):b.restore_check(Path('ignored'),False)
    def test_cli_hides_subprocess_and_storage_exception_payload(self):
        stderr=io.StringIO()
        with patch.object(b,'main',side_effect=RuntimeError('PHI secret DSN SQL')),contextlib.redirect_stderr(stderr):
            self.assertEqual(b.run_cli(),1)
        self.assertNotIn('PHI secret',stderr.getvalue());self.assertNotIn('Traceback',stderr.getvalue())
    def test_artifact_private_and_digest_detects_change(self):
        with tempfile.TemporaryDirectory() as temp:
            p=Path(temp)/'proof.json';b.write(p,{'validated':True})
            self.assertEqual(p.stat().st_mode & 0o777,0o600)
            before=b.digest_file(p);p.write_text('changed')
            self.assertNotEqual(before,b.digest_file(p))
