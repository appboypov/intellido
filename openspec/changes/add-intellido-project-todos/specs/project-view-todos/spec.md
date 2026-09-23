## ADDED Requirements

### Requirement: Add a todo to the selected files and folders
The project view context menu SHALL offer `Add Todo…`. It SHALL ask once for the todo text and add that todo to every selected file and folder in the project, each to the list its location belongs to. Items outside the project and items inside the todos folder SHALL be skipped.

#### Scenario: Two selected items
- **GIVEN** Brian selected folder `src/auth` and file `src/cart/Cart.kt` in the project view
- **WHEN** Brian chooses `Add Todo…` and enters `add tests`
- **THEN** `todos/src/auth.md` holds `- [ ] add tests`
- **AND** `todos/src/cart.md` holds `- [ ] [[Cart.kt]] add tests`

#### Scenario: Cancelled
- **WHEN** Brian chooses `Add Todo…` and cancels the text prompt
- **THEN** no list changes

### Requirement: The action has a bindable shortcut
`Add Todo…` SHALL be a registered IDE action, listed in Settings | Keymap, so the user can assign a shortcut. The shortcut SHALL act on the project view selection, and in an editor on the edited file.

#### Scenario: Brian binds a shortcut
- **GIVEN** Brian assigned `ctrl+alt+T` to `Add Todo…` in the Keymap
- **WHEN** Brian presses `ctrl+alt+T` in the editor of `src/auth/Login.kt`
- **THEN** the todo prompt opens for `Login.kt`
