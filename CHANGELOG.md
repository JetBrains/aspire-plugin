<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# aspire-plugin Changelog

## [Unreleased]

## [1.8.0] - 2025-05-20

### Changed

- Support for Rider 2025.2

## [1.7.10] - 2025-05-16

### Added

- Action to show Aspire resource diagram

### Changed

- Generate `aspire-manifest.json` manifest file instead of `manifest.json`

### Fixed

- [#399](https://github.com/JetBrains/aspire-plugin/issues/399) Show resources even if the `DOTNET_DASHBOARD_UNSECURED_ALLOW_ANONYMOUS` variable is set

## [1.7.9] - 2025-04-16

### Changed

- Support for .NET Aspire 9.2
- Support for Rider 2025.1

### Fixed

- Debugging Blazor WASM projects
- Database resource connection duplication

## [1.7.8] - 2025-03-27

### Fixed

- Do not recreate the dashboard component each time

### Added

- Resource action toolbar to the resource console view
- Setting to prefer resource console view

## [1.7.7] - 2025-03-07

### Fixed

- NavigateToDebugTab action text and description
- [#364](https://github.com/JetBrains/aspire-plugin/issues/364) Aspire does not start projects referenced by path

## [1.7.6] - 2025-03-03

### Added

- Action to navigate to the resource project debug tab
- Action to execute a custom command from the Services dashboard

## [1.7.5] - 2025-02-26

### Changed

- [#354](https://github.com/JetBrains/aspire-plugin/issues/354) Support for AWS lambda projects
- Use specialized icons for database resources 

### Fixed

- [#296](https://github.com/JetBrains/aspire-plugin/issues/296) Database containers whose Lifetime is Permanent should be kept in the Database panel

## [1.7.4] - 2025-02-19

### Fixed

- Various internal fixes

## [1.7.3] - 2025-02-03

### Fixed

- Improve Aspire service displaying

## [1.7.2] - 2025-01-31

### Fixed

- Sync `launchBrowser` flag from an AppHost `launchSettings` file with run configuration

### Changed

- Use a single SessionHost for all project run configurations and tests
- Support for Rider 2025.1 EAP 2

## [1.7.1] - 2025-01-17

### Removed

- Unused dependencies

## [1.7.0] - 2024-12-18

### Changed

- Support for Rider 2025.1

## [1.6.8] - 2024-12-16

### Fixed

- [RIDER-121087](https://youtrack.jetbrains.com/issue/RIDER-121087): Aspire suggests generating a run config on any project

## [1.6.7] - 2024-12-04

### Added

- Setting to hide sensitive property values on the dashboard

### Fixed

- [#119](https://github.com/JetBrains/aspire-plugin/issues/119): Colors in Aspire logs are missing
- [#306](https://github.com/JetBrains/aspire-plugin/issues/306): Can run AppHost under .NET 9
- [#304](https://github.com/JetBrains/aspire-plugin/issues/304): Starting console application project resource does not honor `workingDirectory` option of the launch profile

## [1.6.6] - 2024-11-22

### Changed

- Updated plugin configuration

## [1.6.5] - 2024-11-21

### Fixed

- [#298](https://github.com/JetBrains/aspire-plugin/issues/298) Build a resource project before launching it

### Added

- Attach debugger to a resource dashboard action

## [1.6.4] - 2024-11-14

### Fixed

- [#292](https://github.com/JetBrains/aspire-plugin/issues/292) Fix no runner found for execution type 'IDE' error

## [1.6.3] - 2024-11-13

### Fixed

- Fix resource icon when healthStatus is null

## [1.6.2] - 2024-10-30

### Fixed

- [#243](https://github.com/JetBrains/aspire-plugin/issues/243) Only one database gets added to database panel if multiple databases are present

## [1.6.1] - 2024-10-29

### Added

- Support for new resource properties

## [1.6.0] - 2024-10-24

### Changed

- Support for Rider 2024.3

## [1.5.2] - 2024-10-23

### Added

- Setting to disable browser launch for child projects

## [1.5.1] - 2024-10-21

### Fixed

- [#263](https://github.com/JetBrains/aspire-plugin/issues/263) Opening invalid project url

## [1.5.0] - 2024-10-01

### Added

- [#129](https://github.com/JetBrains/aspire-plugin/issues/129) Support for Blazor Wasm projects
- [#247](https://github.com/JetBrains/aspire-plugin/issues/247) Ability to debug Aspire host projects

## [1.4.1] - 2024-08-08

### Fixed

- [#226](https://github.com/JetBrains/aspire-plugin/issues/226): Cannot restart .NET project services

## [1.4.0] - 2024-07-24

### Added

- Unit testing support
- Target framework, arguments, working directory and podman runtime parameters to the run configuration
- Automatic introspection for the created database

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

[Unreleased]: https://github.com/JetBrains/aspire-plugin/compare/v1.8.0...HEAD
[1.8.0]: https://github.com/JetBrains/aspire-plugin/compare/v1.7.10...v1.8.0
[1.7.10]: https://github.com/JetBrains/aspire-plugin/compare/v1.7.9...v1.7.10
[1.7.9]: https://github.com/JetBrains/aspire-plugin/compare/v1.7.8...v1.7.9
[1.7.8]: https://github.com/JetBrains/aspire-plugin/compare/v1.7.7...v1.7.8
[1.7.7]: https://github.com/JetBrains/aspire-plugin/compare/v1.7.6...v1.7.7
[1.7.6]: https://github.com/JetBrains/aspire-plugin/compare/v1.7.5...v1.7.6
[1.7.5]: https://github.com/JetBrains/aspire-plugin/compare/v1.7.4...v1.7.5
[1.7.4]: https://github.com/JetBrains/aspire-plugin/compare/v1.7.3...v1.7.4
[1.7.3]: https://github.com/JetBrains/aspire-plugin/compare/v1.7.2...v1.7.3
[1.7.2]: https://github.com/JetBrains/aspire-plugin/compare/v1.7.1...v1.7.2
[1.7.1]: https://github.com/JetBrains/aspire-plugin/compare/v1.7.0...v1.7.1
[1.7.0]: https://github.com/JetBrains/aspire-plugin/compare/v1.6.8...v1.7.0
[1.6.8]: https://github.com/JetBrains/aspire-plugin/compare/v1.6.7...v1.6.8
[1.6.7]: https://github.com/JetBrains/aspire-plugin/compare/v1.6.6...v1.6.7
[1.6.6]: https://github.com/JetBrains/aspire-plugin/compare/v1.6.5...v1.6.6
[1.6.5]: https://github.com/JetBrains/aspire-plugin/compare/v1.6.4...v1.6.5
[1.6.4]: https://github.com/JetBrains/aspire-plugin/compare/v1.6.3...v1.6.4
[1.6.3]: https://github.com/JetBrains/aspire-plugin/compare/v1.6.2...v1.6.3
[1.6.2]: https://github.com/JetBrains/aspire-plugin/compare/v1.6.1...v1.6.2
[1.6.1]: https://github.com/JetBrains/aspire-plugin/compare/v1.6.0...v1.6.1
[1.6.0]: https://github.com/JetBrains/aspire-plugin/compare/v1.5.2...v1.6.0
[1.5.2]: https://github.com/JetBrains/aspire-plugin/compare/v1.5.1...v1.5.2
[1.5.1]: https://github.com/JetBrains/aspire-plugin/compare/v1.5.0...v1.5.1
[1.5.0]: https://github.com/JetBrains/aspire-plugin/compare/v1.4.1...v1.5.0
[1.4.1]: https://github.com/JetBrains/aspire-plugin/compare/v1.4.0...v1.4.1
[1.4.0]: https://github.com/JetBrains/aspire-plugin/compare/v1.3.1...v1.4.0
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
