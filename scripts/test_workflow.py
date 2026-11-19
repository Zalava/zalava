import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


WORKFLOW = Path(__file__).with_name('zalava-workflow')


class WorkflowBoundaryTest(unittest.TestCase):
    def test_verify_preserves_gradle_defaults_and_explicit_environment(self):
        subprocess.run(['git', 'init', '-q', self.repo], check=True)
        wrapper = self.repo / 'gradlew'
        wrapper.write_text('#!/usr/bin/env python3\n'
                           'import json, os, sys\n'
                           'from pathlib import Path\n'
                           'Path("invocation.json").write_text(json.dumps({\n'
                           '"home": os.environ.get("GRADLE_USER_HOME"),\n'
                           '"java": os.environ.get("JAVA_HOME"),\n'
                           '"args": sys.argv[1:]}))\n')
        wrapper.chmod(0o755)
        for gradle_home in (None, str(self.repo / 'shared-gradle-home')):
            with self.subTest(gradle_home=gradle_home):
                environment = dict(os.environ)
                environment.pop('GRADLE_USER_HOME', None)
                environment['JAVA_HOME'] = '/example/java-25'
                if gradle_home is not None:
                    environment['GRADLE_USER_HOME'] = gradle_home
                result = subprocess.run([str(WORKFLOW), '--repository', str(self.repo),
                                         'verify', 'check'], env=environment,
                                        capture_output=True, text=True)
                self.assertEqual(result.returncode, 0, result.stderr)
                invocation = json.loads((self.repo / 'invocation.json').read_text())
                self.assertEqual(invocation, {'home': gradle_home,
                                             'java': '/example/java-25',
                                             'args': ['check']})

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


class VerificationImageTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.tools = self.root / 'tools'
        self.tools.mkdir()
        self.compose = self.tools / 'docker-compose'
        self.compose.touch()
        self.calls = self.root / 'calls.jsonl'
        docker = self.tools / 'docker'
        docker.write_text('#!/usr/bin/env python3\n'
                          'import json, os, sys\n'
                          'from pathlib import Path\n'
                          'args = sys.argv[1:]\n'
                          'if args[0] == "info": print(os.environ["COMPOSE_FIXTURE"])\n'
                          'if args[0] == "run":\n'
                          '    with Path(os.environ["DOCKER_CALLS"]).open("a") as out:\n'
                          '        out.write(json.dumps(args) + "\\n")\n'
                          '    sys.exit(int(os.environ.get("DOCKER_RUN_EXIT", "0")))\n')
        docker.chmod(0o755)
        self.environment = dict(os.environ, PATH=str(self.tools) + os.pathsep + os.environ['PATH'],
                                HOME=str(self.root / 'home'), COMPOSE_FIXTURE=str(self.compose),
                                DOCKER_CALLS=str(self.calls))
        self.environment.pop('GRADLE_USER_HOME', None)
        self.wrapper = WORKFLOW.parent.parent / 'ci/playwright/verify-in-image.sh'

    def invoke(self, *arguments):
        return subprocess.run(['bash', str(self.wrapper), 'pinned-image', *arguments],
                              cwd=self.root, env=self.environment, capture_output=True, text=True)

    def invocations(self):
        return [json.loads(line) for line in self.calls.read_text().splitlines()]

    def test_default_keeps_the_complete_gate_and_forwards_options(self):
        result = self.invoke('--rerun-tasks', '--no-build-cache')
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = self.invocations()
        self.assertEqual(len(calls), 2)  # gate plus unused-browser inspection
        gate = calls[0][calls[0].index('./gradlew'):]
        self.assertEqual(gate, ['./gradlew', ':module-api:test', ':module-api-test:check',
                                ':app:check', '--profile', '--rerun-tasks', '--no-build-cache'])
        home = str(self.root / 'home/.gradle')
        self.assertIn('GRADLE_USER_HOME=' + home, calls[0])
        self.assertIn(home + ':' + home, calls[0])

    def test_host_moves_only_the_two_oci_tasks_to_required_runners(self):
        result = self.invoke('--lane', 'host')
        self.assertEqual(result.returncode, 0, result.stderr)
        gate = self.invocations()[0]
        self.assertEqual(gate[gate.index('./gradlew'):],
                         ['./gradlew', ':module-api:test', ':module-api-test:check', ':app:check',
                          '--exclude-task', ':app:ociImageAcceptanceTest', '--exclude-task',
                          ':app:ociFullJreImageAcceptanceTest', '--profile'])

    def test_oci_runners_execute_both_variants_and_compose(self):
        cases = [('oci-default', [':app:ociImageAcceptanceTest', ':app:ociComposeAcceptanceTest']),
                 ('oci-full-jre', [':app:ociFullJreImageAcceptanceTest'])]
        for lane, tasks in cases:
            with self.subTest(lane=lane):
                self.calls.unlink(missing_ok=True)
                result = self.invoke('--lane', lane, '--rerun-tasks')
                self.assertEqual(result.returncode, 0, result.stderr)
                gate = self.invocations()[0]
                self.assertEqual(gate[gate.index('./gradlew'):],
                                 ['./gradlew', *tasks, '--profile', '--rerun-tasks'])

    def test_explicit_gradle_home_is_preserved(self):
        home = str(self.root / 'configured-home')
        self.environment['GRADLE_USER_HOME'] = home
        result = self.invoke()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('GRADLE_USER_HOME=' + home, self.invocations()[0])

    def test_gate_failure_is_propagated_without_running_inspection(self):
        self.environment['DOCKER_RUN_EXIT'] = '37'
        result = self.invoke('--lane', 'host')
        self.assertEqual(result.returncode, 37, result.stderr)
        self.assertEqual(len(self.invocations()), 1)

    def test_invalid_or_missing_lane_fails_before_docker(self):
        for arguments in [('--lane', 'unknown'), ('--lane',)]:
            with self.subTest(arguments=arguments):
                result = self.invoke(*arguments)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse(self.calls.exists())


if __name__ == '__main__':
    unittest.main()
