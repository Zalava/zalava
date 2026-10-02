import subprocess
import tempfile
import unittest
from pathlib import Path


WORKFLOW = Path(__file__).with_name('sea-workflow')


class WorkflowBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.repo = Path(self.directory.name)
        subprocess.run(['git', 'init', '-q', '-b', 'step-test'], cwd=self.repo, check=True)
        (self.repo / 'plan.md').write_text('Plan')
        (self.repo / 'body.md').write_text('PR description')

    def invoke(self, *arguments):
        return subprocess.run([str(WORKFLOW), '--repository', str(self.repo), *arguments],
                              text=True, capture_output=True)

    def test_rejects_unknown_changes_before_verification_or_staging(self):
        (self.repo / 'unrelated.txt').write_text('Do not commit this')
        result = self.invoke('publish', '--plan', str(self.repo / 'plan.md'),
                             '--body-file', str(self.repo / 'body.md'), '--title', 'Test',
                             '--file', 'plan.md', '--file', 'body.md',
                             '--verify-command', 'touch should-not-exist')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('outside explicit allowlist', result.stderr)
        self.assertFalse((self.repo / 'should-not-exist').exists())
        self.assertEqual(subprocess.check_output(['git', 'diff', '--cached', '--name-only'],
                                                cwd=self.repo).strip(), b'')

    def test_rejects_parent_paths(self):
        result = self.invoke('publish', '--plan', str(self.repo / 'plan.md'),
                             '--body-file', str(self.repo / 'body.md'), '--title', 'Test',
                             '--file', '../other')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('repository-relative', result.stderr)

    def test_rejects_non_root_repository(self):
        child = self.repo / 'child'
        child.mkdir()
        result = subprocess.run([str(WORKFLOW), '--repository', str(child), 'verify'],
                                text=True, capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('worktree root', result.stderr)


if __name__ == '__main__':
    unittest.main()
