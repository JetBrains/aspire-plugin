package com.jetbrains.aspire.actions.dashboard.host

import com.jetbrains.aspire.AspireCoreFrontendBundle
import com.jetbrains.aspire.cli.AspireCliCommand

internal class DeployAppHostAction : AspireAppHostDeploymentAction(
    AspireCliCommand::Deploy,
    { appHostName -> AspireCoreFrontendBundle.message("deployment.terminal.deploy.title", appHostName) },
)
