package com.jetbrains.aspire.actions.dashboard.host

import com.jetbrains.aspire.AspireCoreFrontendBundle
import com.jetbrains.aspire.cli.AspireCliCommand

internal class PublishAppHostAction : AspireAppHostDeploymentAction(
    AspireCliCommand::Publish,
    { appHostName -> AspireCoreFrontendBundle.message("deployment.terminal.publish.title", appHostName) },
)
