Owning intent: `/Users/codaveto/Work/intents/intellido-project-todos/intellido-project-todos.md`

## Why

Brian wants to organise todos inside a project without leaving IntelliJ: attach a todo to the file or folder it is about, see every todo in one docked panel, and turn a quick comment in code into a tracked todo. The todos must stay plain Markdown in the repository so other tools, such as Obsidian and agents, can read and edit them.

## What Changes

- New IntelliJ Platform plugin `intellido` (display name IntelliDo), published on JetBrains Marketplace, for IntelliJ Platform 2026.1 (build 261) and later. It follows the structure of Turbo Herdr.
- Todo lists are Markdown files, one todo per `- [ ]` line, in a todos folder whose path and name the user sets per project (default `todos` in the project root).
- The folder mirrors the project: a folder's todos and the todos of the files directly in it share one list, `todos/<folder path>.md`, whose frontmatter names the folder. A file's todo line starts with `[[file name]]`. The project root folder's list is `todos/<project name>.md`. The user can also create standalone lists, such as `Ideas`, that belong to no folder.
- The project view context menu adds a todo to every selected file and folder. The action has a Keymap entry, so the user can bind a shortcut.
- A docked `IntelliDo` tool window shows every list and todo, adds todos quickly, completes and reopens them, creates standalone lists, runs cleanup and opens the file a todo is about.
- Completing a todo only ticks its box. Cleanup removes every completed todo and deletes lists left without todos; it runs on a configurable interval (default 24 hours) and on demand.
- Trigger capture: a line in any project file that matches the configured pattern (default start `//`, contains `#todo`, end `;`, each marker switchable) is cut from its file and recorded as a todo, with its line number, in the list of that file's folder. Detection runs on saved-file changes or on a poll interval, the user's choice. The user can ignore folders, whitelist folders and skip git-ignored files (default on).

Assumptions recorded here rather than asked:
- Watch mode reacts to file contents on disk, the way a file watcher does; IntelliJ writes an edited file on save and on its autosave.
- A trigger with no text left between its markers is not captured and stays in its file.
- Only `- [ ]` and `- [x]` lines are todos; every other line in a list file is kept untouched.

## Capabilities

### New Capabilities
- `todo-lists`: the todos folder, list files and their format, adding, completing and reopening todos, standalone lists, and cleanup.
- `project-view-todos`: adding a todo to selected files and folders from the project view, with a bindable shortcut.
- `todo-tool-window`: the docked panel, quick add, completing, opening a todo's file, manual cleanup and following external edits.
- `trigger-capture`: the configurable pattern, watch or poll detection, cutting the trigger from its file, recording it with its line number, and folder filters.
- `plugin-distribution`: supported IDE builds and Marketplace publishing.

### Modified Capabilities
None. The repository is new.

## Impact

- New public repository https://github.com/appboypov/intellido: Kotlin plugin built with the IntelliJ Platform Gradle Plugin 2.x, with build and publish workflows like Turbo Herdr.
- The plugin writes only inside the todos folder, except that trigger capture edits the file a trigger was cut from; those edits run as undoable IDE commands.
- Settings are stored per project in `.idea/intellido.xml`.
