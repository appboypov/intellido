## Context

A new public repository, `appboypov/intellido`, built like Turbo Herdr (`~/Repos/turbo-herdr`): Kotlin, IntelliJ Platform Gradle Plugin 2.x, IntelliJ IDEA 2026.1.2 as the target, JVM toolchain 25, package `dev.appboypov.intellido`, the same MVVM split with one action registry per panel and the same localhost dispatcher. The trigger semantics follow the PLX trigger watcher, which is moving into the PLX CLI.

## Goals / Non-Goals

Goals: the behaviour in the specs of this change.

Non-goals: editing a todo's text or deleting a single todo from the panel (the list file is plain Markdown and editable in the editor), Windows-specific handling, sharing lists between projects.

## Decisions

### D1. The Markdown list files are the only store (ADR-0001)
There is no index or cache on disk. Every read parses the list files; every change rewrites one list file. A list is:

```markdown
---
folder: src/auth
---

- [ ] split the service
- [ ] [[Login.kt]] rename login()
- [ ] [[Login.kt]]:42 fix redirect
- [x] [[Login.kt]] drop legacy flag ✅ 2026-09-23T14:05
```

A todo line matches `^\s*- \[( |x|X)\] (\[\[name\]\](:line)? )?text( ✅ stamp)?$`. Other lines are kept as they are. A standalone list has no `folder` key. A folder list is found by its path, `<todos>/<folder>.md`; when a file there exists without the matching `folder`, adding to that folder fails with a message instead of mixing lists.

### D2. A plain-file store behind a project repository
`TodoStore` reads and writes list files with `java.nio` and knows nothing of the IDE, so its behaviour is unit tested on temporary folders. `TodoRepository`, a project service, wraps it: before a change it saves unsaved editor documents under the todos folder, after a change it refreshes those files in the VFS, and it publishes the current lists as a `StateFlow<List<TodoList>>` for the panel.

### D3. Trigger matching is one pure function (ADR-0002)
`TriggerMatcher.match(line, pattern)` returns the trigger span, the todo text and the remaining line, or nothing. The rules are those of the trigger-capture spec. Lines that end with a CR keep it.

### D4. Watch reacts to VFS content changes; poll rescans
`TriggerCaptureService`, a project service:
- watch mode subscribes to `VirtualFileManager.VFS_CHANGES` and scans each changed or created file in the project after the event, which fires when IntelliJ saves a file and when a change from outside is refreshed;
- poll mode refreshes the project's content roots in the VFS and scans every content file on a fixed schedule;
- both scan once at startup and on the `intellido.triggers.scan` action.

A scan reads the file's document text, collects its triggers, cuts them in one `WriteCommandAction` (undoable, named "Capture Todos") and saves the document, then adds the todos through the repository. Files are filtered by `TriggerFilter` (todos folder, `.git`, binary file type, the ignore list, then the whitelist), and, when git-ignored files are skipped, by one `git check-ignore --stdin` call per batch. Git answers from the work tree as it is, where the IDE's changelist only knows ignored files after its own status update, which misses files at project open and files written a moment ago. No git or no work tree means nothing counts as ignored. Ignore and whitelist entries are project-relative paths; an entry with `*`, `?` or `[` is a glob matched with `java.nio` glob syntax, any other entry matches that path and everything under it.

### D5. Cleanup runs from a project service on a schedule
At project open, and every hour after that, cleanup runs on a pooled thread; `intellido.cleanup.run` runs it on demand. The age is a per-project setting in hours.

### D6. Named actions live in one registry
`TodoPanelViewService` holds the registry from action name to handler, like Turbo Herdr's `HerdrPanelViewService`. The panel, the IDE actions in `plugin.xml` and the dispatcher `POST /api/intellido?action=<name>&project=<name>&<arg>=<value>` reach the same handler:

| Action | Arguments | Effect |
| --- | --- | --- |
| `intellido.todo.add` | `text`, one of `path` (project-relative file or folder) or `list` (list file path relative to the todos folder) | adds a todo |
| `intellido.todo.addToSelection` | none | prompts and adds to the selected files and folders |
| `intellido.todo.toggle` | `list`, `line` | completes or reopens the todo on that line of the list file |
| `intellido.todo.open` | `list`, `line` | opens the todo's file |
| `intellido.list.create` | `name` | creates a standalone list |
| `intellido.list.new` | none | prompts for a name, then creates |
| `intellido.cleanup.run` | none | runs cleanup |
| `intellido.triggers.scan` | none | scans the whole project for triggers |
| `intellido.panel.refresh` | none | reloads the lists |
| `intellido.panel.read` | none | answers the lists as JSON |
| `intellido.panel.show` | none | shows the panel |
| `intellido.settings.open` | none | opens the settings page |

### D7. Settings are per project
`IntelliDoSettings` is a project `SimplePersistentStateComponent` stored in `.idea/intellido.xml`: todos folder, cleanup age in hours, detection mode, poll seconds, three markers with their switches, skip git-ignored, ignore list, whitelist. The settings page lives under Settings | Tools | IntelliDo; changing detection settings restarts detection.

## Risks / Trade-offs

- A pattern with only the end marker on catches every line ending in it, which can cut code. The settings page says so under the switches; the user decides.
- Watch mode sees an edit only once IntelliJ writes the file, on save or autosave.
- An external edit and a plugin edit to the same list file at the same moment can lose one of them; list files are small and edits are rare.
