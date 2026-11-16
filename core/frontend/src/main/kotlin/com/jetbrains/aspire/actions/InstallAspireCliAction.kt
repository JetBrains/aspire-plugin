package com.jetbrains.aspire.actions

import com.jetbrains.aspire.AspireCoreFrontendBundle
import com.jetbrains.aspire.cli.AspireCliCommand

internal class InstallAspireCliAction : AspireCliCommandAction(
    AspireCliCommand.Install,
    { AspireCoreFrontendBundle.message("cli.install.terminal.title") },
)
