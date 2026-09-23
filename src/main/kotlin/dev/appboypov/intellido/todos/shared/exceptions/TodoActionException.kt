package dev.appboypov.intellido.todos.shared.exceptions

/** An action could not run because of its input or the lists on disk; the message is shown to the user as is. */
class TodoActionException(message: String) : RuntimeException(message)
