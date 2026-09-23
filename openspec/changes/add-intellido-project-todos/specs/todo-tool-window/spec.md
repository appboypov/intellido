## ADDED Requirements

### Requirement: The IntelliDo panel docks in the IDE
The plugin SHALL register a docked `IntelliDo` tool window, like the Project and Turbo Herdr panels, that shows every list in the todos folder with its todos. Each list SHALL show its folder path or standalone name; each todo SHALL show its checkbox, file link, line number and text.

#### Scenario: Brian opens the panel
- **GIVEN** lists `todos/src/auth.md` and `todos/Ideas.md` exist
- **WHEN** Brian opens the IntelliDo panel
- **THEN** it shows the lists `src/auth` and `Ideas` with their todos

### Requirement: Quick add
The panel SHALL have a text field that adds its text as a todo when Brian presses Enter. The todo SHALL go to the list of the selected row; with nothing selected it SHALL go to the project root folder's list.

#### Scenario: Brian adds to the selected list
- **GIVEN** the list `Ideas` is selected
- **WHEN** Brian types `dark mode` in the quick-add field and presses Enter
- **THEN** `todos/Ideas.md` holds `- [ ] dark mode` and the panel shows it
- **AND** the field is empty again

### Requirement: Completing from the panel
Ticking a todo's checkbox SHALL complete it, and unticking SHALL reopen it, per the todo-lists requirement.

#### Scenario: Brian ticks a todo
- **WHEN** Brian ticks `split the service` in the panel
- **THEN** its line in `todos/src/auth.md` is completed with a stamp

### Requirement: Opening a todo
Double-clicking a todo SHALL open the file it names, at its caught line when it has one. A todo without a file SHALL open its list file at the todo's line.

#### Scenario: Captured todo
- **GIVEN** the todo `[[Login.kt]]:42 fix redirect` in list `src/auth`
- **WHEN** Brian double-clicks it
- **THEN** the editor opens `src/auth/Login.kt` at line 42

### Requirement: Panel actions
The panel header SHALL offer New List, Run Cleanup, Refresh and Settings.

#### Scenario: Brian runs cleanup
- **GIVEN** a todo completed 3 days ago
- **WHEN** Brian chooses Run Cleanup
- **THEN** the todo is gone from the list file and the panel

### Requirement: The panel follows external edits
The panel SHALL reload when a file under the todos folder changes, including edits in the IDE editor after they are saved and changes from outside the IDE.

#### Scenario: Brian edits a list by hand
- **WHEN** Brian adds `- [ ] ship it` to `todos/Ideas.md` in the editor and saves
- **THEN** the panel shows `ship it` under `Ideas`
