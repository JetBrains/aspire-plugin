package com.jetbrains.aspire.rider.run.file

import com.jetbrains.rider.build.tasks.BeforeRunTaskWithProject

class BuildAspireFileBeforeRunTask : BeforeRunTaskWithProject<BuildAspireFileBeforeRunTask>(BuildAspireFileBeforeRunTaskProvider.providerId)
