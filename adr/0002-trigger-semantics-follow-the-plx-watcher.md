# 2. Trigger semantics follow the PLX trigger watcher

Date: 2026-09-23

## Status

Accepted

## Context

Brian marks work in code with a comment trigger, for example `// fix login #todo;`. The PLX trigger watcher, now moving into the PLX CLI, already defines how such a trigger is found and cut. IntelliDo adds that each of the three markers can be switched off.

## Decision

A trigger runs from its start marker (the nearest one before the contains marker) through an end marker that is the last non-whitespace text on its line. It is cut from its line, keeping the text before it; a line left blank or holding only a list marker is deleted. With a marker switched off: no start means the trigger begins at the contains marker, or at the first non-whitespace character without one; no end means it runs to the end of the line. Markers are literal and case-sensitive.

## Consequences

- A trigger written for the PLX CLI behaves the same in IntelliDo with the same markers.
- One pure function implements the rules and carries their unit tests.
