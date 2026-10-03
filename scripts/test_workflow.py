import subprocess
import os
import tempfile
import unittest
from pathlib import Path


WORKFLOW = Path(__file__).with_name('zalava-workflow')


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
        self.repo = Path(self.directory.name) / "public"
        self.repo.mkdir()
        self.maintainer = Path(self.directory.name) / "private"
        self.maintainer.mkdir()
        subprocess.run(['git', 'init', '-q', '-b', 'main'], cwd=self.maintainer, check=True)
        self.plan = self.maintainer / 'docs' / 'plans' / '2026-10-03-example.md'
        self.plan.parent.mkdir(parents=True)
        self.plan.write_text('Private plan')
        subprocess.run(['git', 'init', '-q', '-b', 'step-test'], cwd=self.repo, check=True)
        (self.repo / 'body.md').write_text('PR description')

    def invoke(self, *arguments):
        if arguments[0] == 'publish':
            arguments = (*arguments, '--maintainer-repository', str(self.maintainer))
        return subprocess.run([str(WORKFLOW), '--repository', str(self.repo), *arguments],
                              text=True, capture_output=True)

    def test_rejects_unknown_changes_before_verification_or_staging(self):
        (self.repo / 'unrelated.txt').write_text('Do not commit this')
        result = self.invoke('publish', '--plan', str(self.plan),
                             '--body-file', str(self.repo / 'body.md'), '--title', 'Test',
                             '--file', 'plan.md', '--file', 'body.md',
                             '--verify-command', 'touch should-not-exist')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('outside explicit allowlist', result.stderr)
        self.assertFalse((self.repo / 'should-not-exist').exists())
        self.assertEqual(subprocess.check_output(['git', 'diff', '--cached', '--name-only'],
                                                cwd=self.repo).strip(), b'')

    def test_rejects_parent_paths(self):
        result = self.invoke('publish', '--plan', str(self.plan),
                             '--body-file', str(self.repo / 'body.md'), '--title', 'Test',
                             '--file', '../other')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('repository-relative', result.stderr)

    def test_external_private_plan_reaches_gate_without_staging_it(self):
        result = self.invoke('publish', '--plan', str(self.plan),
                             '--body-file', str(self.repo / 'body.md'), '--title', 'Test',
                             '--file', 'body.md', '--verify-command', 'exit 37')
        self.assertIn("exit status 37", result.stderr)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(subprocess.check_output(['git', 'diff', '--cached', '--name-only'], cwd=self.repo).strip(), b'')
        self.assertFalse((self.repo / 'docs' / 'plans').exists())

    def test_rejects_public_plan_before_verification(self):
        public_plan = self.repo / 'docs' / 'plans' / self.plan.name
        public_plan.parent.mkdir(parents=True)
        public_plan.write_text('Must stay private')
        result = self.invoke('publish', '--plan', str(self.plan),
                             '--body-file', str(self.repo / 'body.md'), '--title', 'Test',
                             '--file', 'body.md', '--file', 'docs/plans',
                             '--verify-command', 'exit 37')
        self.assertIn('public execution plans are forbidden', result.stderr)
        self.assertNotIn("exit status 37", result.stderr)

    def test_rejects_plan_outside_declared_private_repository(self):
        other = Path(self.directory.name) / 'other-plan.md'
        other.write_text('Unowned')
        result = self.invoke('publish', '--plan', str(other),
                             '--body-file', str(self.repo / 'body.md'), '--title', 'Test', '--file', 'body.md')
        self.assertIn('private zalava-dev docs/plans', result.stderr)

    def test_rejects_non_root_repository(self):
        child = self.repo / 'child'
        child.mkdir()
        result = subprocess.run([str(WORKFLOW), '--repository', str(child), 'verify'],
                                text=True, capture_output=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('worktree root', result.stderr)


if __name__ == '__main__':
    unittest.main()
