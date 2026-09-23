## ADDED Requirements

### Requirement: Configurable trigger pattern
A trigger pattern SHALL have three literal, case-sensitive markers: start (default `//`), contains (default `#todo`) and end (default `;`). Each marker SHALL be switchable on or off per project, and at least one SHALL be on.

A line holds a trigger when:
- with contains on, the contains marker occurs; with start also on, a start marker occurs before it and the nearest one begins the trigger; with start off, the contains marker begins it;
- with contains off and start on, the first start marker begins the trigger;
- with start and contains off, the first non-whitespace character begins it;
- with end on, the end marker is the last non-whitespace text on the line and comes after the start and contains markers; the trigger runs through it. With end off, the trigger runs to the end of the line.

The todo text SHALL be the trigger without its markers, trimmed. A trigger with empty text SHALL NOT be captured. A line holds at most one trigger.

#### Scenario Outline: Default pattern
- **GIVEN** the default pattern
- **WHEN** a saved file line reads `<line>`
- **THEN** the captured text is `<text>`

| line | text |
| --- | --- |
| `// fix login #todo;` | `fix login` |
| `// #todo fix login;` | `fix login` |
| `val x = 1 // #todo fix;` | `fix` |
| `// #todo fix login` | nothing |
| `// fix login;` | nothing |

### Requirement: Captured todos are cut from their file and recorded
A captured trigger SHALL be cut from its line, keeping the text before it with trailing whitespace removed. A line left blank, or holding only a list marker such as `-` or `- [ ]`, SHALL be deleted. The todo SHALL be added to the list of the file's folder as `- [ ] [[<file name>]]:<line> <text>`, where `<line>` is the one-based line the trigger was on. The edit SHALL be undoable in the IDE.

#### Scenario: Trailing trigger
- **GIVEN** line 42 of `src/auth/Login.kt` reads `redirect() // #todo fix redirect;`
- **WHEN** the file is saved
- **THEN** line 42 reads `redirect()`
- **AND** `todos/src/auth.md` holds `- [ ] [[Login.kt]]:42 fix redirect`

#### Scenario: Whole-line trigger
- **GIVEN** line 7 of `src/auth/Login.kt` reads `    // #todo add logout;`
- **WHEN** the file is saved
- **THEN** that line is deleted from `Login.kt`

### Requirement: Watch or poll detection
The user SHALL choose per project between watch mode, which reacts to changed files on disk, and poll mode, which scans the project every configured number of seconds (default 30). Both modes SHALL scan the whole project when the project opens and when the user asks for a scan.

#### Scenario: Poll mode
- **GIVEN** poll mode with 30 seconds
- **WHEN** a trigger is written to a file from outside the IDE
- **THEN** it is captured within about 30 seconds

### Requirement: Folder filters
Trigger capture SHALL skip the todos folder, `.git` and binary files. It SHALL skip git-ignored files when "Skip git-ignored files" is on, the default. The user SHALL be able to list ignored paths and whitelisted paths, project-relative, each a folder or file path or a glob such as `**/generated/**`. With a non-empty whitelist, only files under a whitelisted path SHALL be scanned. An ignored path SHALL win over the whitelist.

#### Scenario: Ignored folder
- **GIVEN** `docs` is in the ignore list
- **WHEN** `docs/guide.md` saves a line `// #todo example;`
- **THEN** the line stays and no todo is recorded

#### Scenario: Whitelist
- **GIVEN** the whitelist holds only `src`
- **WHEN** `scripts/run.sh` saves a line `// #todo fix;`
- **THEN** the line stays and no todo is recorded
