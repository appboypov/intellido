# IntelliDo

IntelliDo keeps a project's todos inside the project, as plain Markdown checkbox lists that you can read, diff and commit. It is a plugin for IntelliJ IDEA and the other JetBrains IDEs, from build 261 (2026.1) onwards.

## What it does

- **Lists per folder.** Right-click any file or folder in the project view and choose **Add Todo…**. A folder and its files share one list, which mirrors the folder inside the todos folder. For example, `src/auth/Login.kt` goes to `todos/src/auth.md` and the project root goes to `todos/<project-name>.md`. Lists that belong to no folder can be created from the panel.
- **The IntelliDo panel.** A docked tool window with quick add, a checkbox per todo and double-click to open the todo's file at its line.
- **Trigger capture.** Write `// fix redirect #todo;` anywhere in a project file. IntelliDo cuts the trigger out of the file and records `[[Login.kt]]:5 fix redirect` in the folder's list. The cut is one undoable command.
- **Cleanup.** Completed todos older than the configured age (24 hours by default) are removed when the project opens, every hour and on demand. Lists left without todos are deleted.

A list file looks like this:

```markdown
---
folder: src/auth
---

- [ ] [[Login.kt]]:5 fix redirect
- [x] [[Login.kt]] add logout ✅ 2026-05-20T14:03
```

## Settings

Settings | Tools | IntelliDo, stored per project in `.idea/intellido.xml`:

| Setting | Default |
|---|---|
| Todos folder | `todos` |
| Remove completed todos after | 24 hours |
| Trigger markers: starts with / contains / ends with | `//` / `#todo` / `;`, each can be switched off |
| Detection | Watch: capture when a file is saved or changed on disk. Poll: scan the project every N seconds (30 by default) |
| Skip git-ignored files | On |
| Ignored and whitelisted paths | Project-relative folders, files or globs. Ignore wins over the whitelist |

## Automation

Every panel interaction is a named action. Outside the IDE, the built-in server runs the same actions:

```sh
curl -X POST 'http://127.0.0.1:63342/api/intellido?project=<name>&action=intellido.panel.read'
curl -X POST 'http://127.0.0.1:63342/api/intellido?project=<name>&action=intellido.todo.add&path=src/auth&text=add%20logout'
```

The actions are `intellido.todo.add`, `intellido.todo.toggle`, `intellido.todo.open`, `intellido.list.create`, `intellido.cleanup.run`, `intellido.triggers.scan`, `intellido.panel.refresh`, `intellido.panel.read`, `intellido.panel.show`, `intellido.panel.capture` and `intellido.settings.open`.

## Development

```sh
./gradlew check buildPlugin verifyPlugin   # tests, plugin zip, Plugin Verifier
./gradlew runIde                            # sandbox IDE with the plugin
```

The design and requirements live in `openspec/changes/add-intellido-project-todos/`, and the decisions in `adr/`.

## License

MIT
