## 1. Project scaffold

- [x] 1.1 Scaffold the Kotlin plugin like Turbo Herdr: plugin id `dev.appboypov.intellido`, name IntelliDo, `sinceBuild = 261`, IntelliJ IDEA 2026.1.2, JVM toolchain 25, logging and bundle helpers. Verify: `./gradlew buildPlugin` produces a zip.
- [ ] 1.2 Add `.crabbox.yaml`, build and publish workflows and an MIT license. Verify: the build workflow passes on GitHub.

## 2. Todo lists (D1, D2, D5)

- [x] 2.1 Implement the list format: parse and write list files, todo lines, frontmatter and completion stamps. Verify: unit tests round-trip the design D1 example and keep non-todo lines.
- [x] 2.2 Implement `TodoStore`: add to a folder, a file or a standalone list; toggle; create lists; cleanup with age, stamping, empty-file and empty-folder deletion. Verify: unit tests on temporary folders cover every scenario in the todo-lists spec.
- [x] 2.3 Implement `TodoRepository` with document saving, VFS refresh, a `StateFlow` of lists and scheduled cleanup. Verify: sandbox IDE shows the lists and hourly cleanup is scheduled.

## 3. Trigger capture (D3, D4)

- [x] 3.1 Implement `TriggerMatcher`. Verify: unit tests cover the default pattern table, marker switches, list-marker lines and empty text.
- [x] 3.2 Implement `TriggerFilter`. Verify: unit tests cover ignore paths, globs, whitelist and ignore-over-whitelist.
- [x] 3.3 Implement `TriggerCaptureService` with watch and poll modes. Verify: sandbox IDE, saving `// #todo fix;` in a file cuts it and records the todo with its line.

## 4. Panel, actions and settings (D6, D7)

- [ ] 4.1 Settings state and settings page. Verify: sandbox IDE, a changed todos folder persists in `.idea/intellido.xml`.
- [x] 4.2 Action registry, IDE actions, project view `Add Todo…` and dispatcher. Verify: dispatcher calls run each action.
- [x] 4.3 The IntelliDo tool window with quick add, checkboxes, open on double-click and header actions. Verify: screenshot of the sandbox IDE panel after adding, completing and cleanup.

## 5. Release

- [x] 5.1 Plugin Verifier passes for build 261 and the recommended IDEs. Verify: `./gradlew verifyPlugin`.
