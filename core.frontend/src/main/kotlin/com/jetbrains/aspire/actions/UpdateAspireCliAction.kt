package com.jetbrains.aspire.actions

import com.jetbrains.aspire.AspireCoreFrontendBundle
import com.jetbrains.aspire.cli.AspireCliCommand

internal class UpdateAspireCliAction : AspireCliCommandAction(
    AspireCliCommand.SelfUpdate,
    { AspireCoreFrontendBundle.message("cli.update.terminal.title") },
)
