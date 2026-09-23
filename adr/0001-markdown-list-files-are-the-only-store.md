# 1. Markdown list files are the only store

Date: 2026-09-23

## Status

Accepted

## Context

IntelliDo keeps project todos. Brian wants them in plain Markdown inside the repository, readable and editable by people, Obsidian and agents without the plugin.

## Decision

The list files under the todos folder are the only store. There is no index, cache or database. Each list is one Markdown file, one todo per `- [ ]` or `- [x]` line; the folder a list belongs to is its `folder` frontmatter; a file's todo starts with `[[file name]]`, with `:line` when it was captured; completing a todo only ticks its box. Non-todo lines are preserved.

## Consequences

- Any edit to a list file, in or outside the IDE, is the truth the panel shows after a reload.
- The format is a public contract: changing it needs a new ADR and a migration of existing lists.
- Reads parse files every time, which is cheap for the size of todo lists.
