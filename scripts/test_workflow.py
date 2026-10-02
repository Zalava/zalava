import subprocess
import os
import tempfile
import unittest
from pathlib import Path


WORKFLOW = Path(__file__).with_name('sea-workflow')


class WorkflowBoundaryTest(unittest.TestCase):
    def test_stack_start_checks_out_registered_branch(self):
        subprocess.run(['git', 'add', '.'], cwd=self.repo, check=True)
        subprocess.run(['git', '-c', 'user.name=Test', '-c', 'user.email=test@example.invalid',
                        'commit', '-qm', 'Fixture'], cwd=self.repo, check=True)
        with tempfile.TemporaryDirectory() as tool_directory:
            gh = Path(tool_directory) / 'gh'
            gh.write_text('#!/bin/sh\nif [ "$2" = "add" ]; then git branch "$3"; fi\n')
            gh.chmod(0o755)
            result = subprocess.run([str(WORKFLOW), '--repository', str(self.repo),
                                     'stack-start', 'step-registered'], text=True, capture_output=True,
                                    env=dict(os.environ, PATH=tool_directory + os.pathsep + os.environ['PATH']))
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(subprocess.check_output(['git', 'branch', '--show-current'],
                                                cwd=self.repo, text=True).strip(), 'step-registered')

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
