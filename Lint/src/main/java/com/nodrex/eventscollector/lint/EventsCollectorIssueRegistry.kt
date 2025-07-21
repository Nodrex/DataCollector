package com.nodrex.eventscollector.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.Issue


class EventsCollectorIssueRegistry : IssueRegistry() {
    override val issues: List<Issue> = listOf(EmitTypeCheckDetector.ISSUE)

    override val api: Int = com.android.tools.lint.detector.api.CURRENT_API
}