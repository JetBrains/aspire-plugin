<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# aspire-plugin Changelog

## [Unreleased]

### Added

- Unit testing support

## [1.3.1] - 2024-07-17

### Changed

- Plugin repository and vendor

## [1.3.0] - 2024-07-10

### Changed

- Support for Rider 2024.2

### Removed

- Experimental OpenTelemetry support

## [1.2.0] - 2024-06-06

### Added

- [#177](https://github.com/rafaelldi/aspire-plugin/issues/177): Support `commandLineArgs` for the host projects from `launchSettings.json`
- New actions to the dashboard to run, debug and stop the host project

## [1.0.1] - 2024-05-27

### Fixed

- [#168](https://github.com/rafaelldi/aspire-plugin/issues/168): Aspire plugin does not boot dashboard

## [1.0.0] - 2024-05-24

### Changed

- Support for .NET Aspire 8.0.1

### Fixed

- [#140](https://github.com/rafaelldi/aspire-plugin/issues/140): Console output is incorrectly formatted when debugging code

## [0.7.1] - 2024-05-14

### Added

- Stop and Restart Resource actions to the dashboard
- Hot reload while running

## [0.7.0] - 2024-05-08

### Changed

- Support for .NET Aspire preview 7

## [0.6.1] - 2024-04-30

### Added

- Selector for launch profile in the run configuration
- Generate Aspire Host run configuration for each launchSettings profile

### Fixed

- [#125](https://github.com/rafaelldi/aspire-plugin/issues/125): Start session request handling

## [0.6.0] - 2024-04-25

### Changed

- Support for .NET Aspire preview 6

## [0.5.0] - 2024-04-12

### Changed

- [#109](https://github.com/rafaelldi/aspire-plugin/issues/109): Support for .NET Aspire preview 5

### Fixed

- [111](https://github.com/rafaelldi/aspire-plugin/issues/111): After starting the Aspire configuration, nothing happens

## [0.4.3] - 2024-04-09

### Changed

- Support for Rider 2024.1

### Fixed

- Manifest generation action
- [#102](https://github.com/rafaelldi/aspire-plugin/issues/102): Timeout from DCP

## [0.4.2] - 2024-04-02

### Changed

- Run configurations are no longer used to launch child projects
- Aspire host logs are displayed in the services tree node

## [0.4.1] - 2024-03-18

### Added

- Automatic connection to the database

## [0.4.0] - 2024-03-13

### Changed

- Support for .NET Aspire preview 4

## [0.3.0] - 2024-02-29

### Changed

- Update Aspire dashboard
- Support for Rider 2024.1

### Removed

- Setting to show services

## [0.2.4] - 2024-02-13

### Added

- Generate Aspire manifest action
- Update Aspire version to 8.0.0-preview.3.24105.21

## [0.2.3] - 2024-01-22

### Fixed

- [#39](https://github.com/rafaelldi/aspire-plugin/issues/39): Trace diagram can be broken because of the deprecated span attributes
- Fixed show Aspire dashboard action.
- [#24](https://github.com/rafaelldi/aspire-plugin/issues/24): Substitute environment variables for all Aspire projects 

### Changed

- Improve traces for gRPC and PostgreSQL

## [0.2.2] - 2024-01-16

### Added

- [#37](https://github.com/rafaelldi/aspire-plugin/issues/37): Action to show a diagram based on distributed traces
- Added project urls to the Services view

### Changed

- [#24](https://github.com/rafaelldi/aspire-plugin/issues/24): Improved handling of environment variables from Aspire host launchSettings file

## [0.2.1] - 2024-01-05

### Fixed

- A possible NullReferenceException when calling API by Aspire host

## [0.2.0] - 2024-01-02

### Added

- Dashboard in the Services tool window
- Metrics table and chart
- Troubleshooting page in the docs

### Fixed

- [#23](https://github.com/rafaelldi/aspire-plugin/issues/23): Aspire workload update on macOS and Linux

## [0.1.2] - 2023-12-21

### Added

- Documentation: https://rafaelldi.github.io/aspire-plugin/starter-topic.html
- Suggest updating Aspire workload

### Fixed

- Fix an issue when the Aspire host can exceed the execution time of the run configuration

## [0.1.1] - 2023-12-12

### Changed

- Require restart after installing

### Added

- Plugin icon and run configuration icon

## [0.1.0] - 2023-12-08

### Added

- Support for running and debugging of Aspire projects

[Unreleased]: https://github.com/JetBrains/aspire-plugin/compare/v1.3.1...HEAD
[1.3.1]: https://github.com/JetBrains/aspire-plugin/compare/v1.3.0...v1.3.1
[1.3.0]: https://github.com/JetBrains/aspire-plugin/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/JetBrains/aspire-plugin/compare/v1.0.1...v1.2.0
[1.0.1]: https://github.com/JetBrains/aspire-plugin/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/JetBrains/aspire-plugin/compare/v0.7.1...v1.0.0
[0.7.1]: https://github.com/JetBrains/aspire-plugin/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/JetBrains/aspire-plugin/compare/v0.6.1...v0.7.0
[0.6.1]: https://github.com/JetBrains/aspire-plugin/compare/v0.6.0...v0.6.1
[0.6.0]: https://github.com/JetBrains/aspire-plugin/compare/v0.5.0...v0.6.0
[0.5.0]: https://github.com/JetBrains/aspire-plugin/compare/v0.4.3...v0.5.0
[0.4.3]: https://github.com/JetBrains/aspire-plugin/compare/v0.4.2...v0.4.3
[0.4.2]: https://github.com/JetBrains/aspire-plugin/compare/v0.4.1...v0.4.2
[0.4.1]: https://github.com/JetBrains/aspire-plugin/compare/v0.4.0...v0.4.1
[0.4.0]: https://github.com/JetBrains/aspire-plugin/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/JetBrains/aspire-plugin/compare/v0.2.4...v0.3.0
[0.2.4]: https://github.com/JetBrains/aspire-plugin/compare/v0.2.3...v0.2.4
[0.2.3]: https://github.com/JetBrains/aspire-plugin/compare/v0.2.2...v0.2.3
[0.2.2]: https://github.com/JetBrains/aspire-plugin/compare/v0.2.1...v0.2.2
[0.2.1]: https://github.com/JetBrains/aspire-plugin/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/JetBrains/aspire-plugin/compare/v0.1.2...v0.2.0
[0.1.2]: https://github.com/JetBrains/aspire-plugin/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/JetBrains/aspire-plugin/compare/v0.1.0...v0.1.1
[0.1.0]: https://github.com/JetBrains/aspire-plugin/commits/v0.1.0
