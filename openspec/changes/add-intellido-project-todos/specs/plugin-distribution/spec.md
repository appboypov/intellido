## ADDED Requirements

### Requirement: Supported IDE builds
The plugin SHALL install and run on IntelliJ Platform IDEs from build 261 (2026.1) onward and SHALL pass the IntelliJ Plugin Verifier for that range.

#### Scenario: Brian installs on IntelliJ IDEA 2026.1.2
- **GIVEN** IntelliJ IDEA 2026.1.2
- **WHEN** Brian installs the plugin zip
- **THEN** the IntelliDo panel and `Add Todo…` are available without other plugins

### Requirement: Marketplace publishing
The repository SHALL build and test the plugin on every push and pull request, and SHALL publish to JetBrains Marketplace when a `v*` tag is pushed, using the `JETBRAINS_MARKETPLACE_TOKEN` repository secret.

#### Scenario: A tagged release
- **GIVEN** the secret `JETBRAINS_MARKETPLACE_TOKEN` is set
- **WHEN** tag `v0.1.0` is pushed
- **THEN** the publish workflow uploads the plugin to JetBrains Marketplace
