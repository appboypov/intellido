## ADDED Requirements

### Requirement: Todos live in a configurable todos folder
The plugin SHALL keep every todo list as a Markdown file under the project's todos folder. The folder path SHALL be configurable per project, relative to the project root or absolute, and SHALL default to `todos` in the project root.

#### Scenario: Default folder
- **GIVEN** project `shop` with default settings
- **WHEN** Brian adds a todo to folder `src/auth`
- **THEN** the file `shop/todos/src/auth.md` holds the todo

#### Scenario: Custom folder
- **GIVEN** Brian set the todos folder of project `shop` to `docs/tasks`
- **WHEN** Brian adds a todo to folder `src/auth`
- **THEN** the file `shop/docs/tasks/src/auth.md` holds the todo

### Requirement: A folder and its files share one list
The list of a project folder SHALL be `<todos folder>/<folder path>.md`, and the project root folder's list SHALL be `<todos folder>/<project name>.md`. The list SHALL carry frontmatter `folder: <folder path>`, with `.` for the project root. A todo on the folder SHALL read `- [ ] <text>`. A todo on a file directly in that folder SHALL go to the same list and read `- [ ] [[<file name>]] <text>`.

#### Scenario: File and folder todos in one list
- **GIVEN** no list exists for folder `src/auth`
- **WHEN** Brian adds `split the service` to folder `src/auth`
- **AND** Brian adds `rename login()` to file `src/auth/Login.kt`
- **THEN** `todos/src/auth.md` reads:
  ```markdown
  ---
  folder: src/auth
  ---

  - [ ] split the service
  - [ ] [[Login.kt]] rename login()
  ```

#### Scenario: Root file
- **WHEN** Brian adds `update badges` to file `README.md` in the root of project `shop`
- **THEN** `todos/shop.md` has frontmatter `folder: .` and the line `- [ ] [[README.md]] update badges`

### Requirement: Standalone lists
The user SHALL be able to create a list by name that belongs to no folder. Its file SHALL be `<todos folder>/<name>.md` without a `folder` in its frontmatter. Creating a list whose file already exists SHALL fail with a message naming the file.

#### Scenario: Brian creates an Ideas list
- **WHEN** Brian creates the list `Ideas` and adds `dark mode` to it
- **THEN** `todos/Ideas.md` holds `- [ ] dark mode` and has no `folder` frontmatter

### Requirement: Completing and reopening
Completing a todo SHALL tick its box and reopening SHALL clear it; nothing else on the line SHALL change. Lines that are not todos SHALL stay untouched.

#### Scenario: Brian completes a todo
- **GIVEN** `todos/src/auth.md` holds `- [ ] [[Login.kt]]:42 fix redirect`
- **WHEN** Brian completes it
- **THEN** the line reads `- [x] [[Login.kt]]:42 fix redirect`

### Requirement: Editing a todo
The user SHALL be able to replace a todo's text. Its box and file link SHALL stay. Empty text SHALL be refused.

#### Scenario: Brian rewords a todo
- **GIVEN** `todos/src/auth.md` holds `- [ ] [[Login.kt]]:42 fix redirect`
- **WHEN** Brian edits it to `fix the login redirect`
- **THEN** the line reads `- [ ] [[Login.kt]]:42 fix the login redirect`

### Requirement: Cleanup removes completed todos on an interval
Cleanup SHALL remove every completed todo and SHALL delete a list file that holds no todo afterwards, together with folders under the todos folder left empty. Cleanup SHALL run once the configured interval has passed since its last run, checked when the project opens and every hour while it is open, and when the user asks for it. The interval SHALL be configurable in hours per project and SHALL default to 24. The time of the last run SHALL be kept outside the list files.

#### Scenario: Completed todo is removed
- **GIVEN** a completed todo and an open todo in `todos/src/auth.md`
- **WHEN** cleanup runs
- **THEN** the completed todo is gone and the open todo remains

#### Scenario: Empty list is deleted
- **GIVEN** `todos/src/auth.md` holds only a completed todo
- **WHEN** cleanup runs
- **THEN** `todos/src/auth.md` no longer exists
- **AND** `todos/src` is deleted when it is empty

#### Scenario: Interval not yet passed
- **GIVEN** cleanup ran 3 hours ago and the interval is 24 hours
- **WHEN** the hourly check runs
- **THEN** no todo is removed
