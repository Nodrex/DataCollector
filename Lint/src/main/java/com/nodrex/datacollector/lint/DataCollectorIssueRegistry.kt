package com.nodrex.datacollector.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.Issue


class DataCollectorIssueRegistry : IssueRegistry() {
    override val issues: List<Issue> = listOf(EmitTypeCheckDetector.ISSUE, AnnotationDetector.ISSUE)

    override val api: Int = com.android.tools.lint.detector.api.CURRENT_API
}