# Prerequisites
* JDK 21;
* .NET 9 - should be installed automatically when using `dotnet.cmd` script.

# Build

To build the plugin, execute this shell command:

```console
$ ./gradlew buildPlugin
```

After that, the plugin ZIP distribution will be created in the `build/distributions` directory.

# Run IDE

The following command will build the plugin and run it using a sandboxed
instance of Rider (set the required version via `gradle.properties`).

```console
$ ./gradlew runIde
```

# Test

Execute the following shell command:

```console
$ ./gradlew check
```

# Development
* To develop Kotlin part, open the root folder.
* To develop C# part, open `AspirePlugin.slnx` file in the root folder. 
Before that, the `./gradlew prepareDotnetPart` or `./gradlew buildPlugin` should be executed.
* To connect them, there is a `protocol` subfolder. To read more about this protocol, see [this library](https://github.com/JetBrains/rd).

# Run/Debug
* Run/Debug plugin with `Run Plugin` run configuration (from Kotlin part).
* Generate protocol models with `Generate Protocol` run configuration (from Kotlin part).

# Documentation
* [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html#getting-started)
* [ReSharper Platform SDK](https://www.jetbrains.com/help/resharper/sdk/welcome.html)