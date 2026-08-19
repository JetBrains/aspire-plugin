package com.jetbrains.aspire.run.cli

import org.jetbrains.annotations.ApiStatus

/**
 * Log levels accepted by `aspire run --log-level`.
 *
 * The constant [name] is passed to the CLI verbatim and is also the persisted form,
 * so the constants must not be renamed (reordering them is safe).
 */
@ApiStatus.Internal
enum class AspireCliLogLevel {
    Trace, Debug, Information, Warning, Error, Critical, None
}
