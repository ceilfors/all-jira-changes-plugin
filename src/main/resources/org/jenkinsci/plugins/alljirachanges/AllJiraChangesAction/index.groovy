/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Wisen Tanasa
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.alljirachanges.AllJiraChangesAction

import com.google.common.collect.Multimap
import hudson.Functions
import hudson.model.AbstractBuild
import hudson.plugins.jira.JiraIssue
import hudson.scm.ChangeLogSet

l = namespace(lib.LayoutTagLib)
st = namespace("jelly:stapler")

l.layout(title: _("title", my.project.name)) {
    st.include(page: "sidepanel.jelly", it: my.project)
    l.main_panel() {
        def from = buildNumber(request.getParameter('from'));
        def to = buildNumber(request.getParameter('to'));

        h1("All JIRA Changes")
        def builds = Functions.filter(my.project.buildsAsMap, from, to).values()
        if (builds.empty) {
            text(_("No builds."))
        } else {
            showChanges(builds)
        }
    }
}

private buildNumber(String build) {
    if (build?.isInteger()) {
        return build
    } else {
        def permaLink = my.project.getPermalinks().get(build)
        def run = permaLink?.resolve(my.project)
        return run?.number?.toString()
    }
}

private showChanges(Collection<AbstractBuild> builds) {
    boolean hadChanges = false;
    for (AbstractBuild build in builds) {
        Multimap<JiraIssue, ChangeLogSet.Entry> jiraIssueMultimap = my.getAllJiraChanges(build);
        if (jiraIssueMultimap.empty) {
            continue
        }
        hadChanges = true
        h2() {
            a(href: "${my.project.absoluteUrl}/${build.number}/changes",
                    "${build.displayName} (${my.getDateFormat().format(build.timestamp.time)})")
        }

        for (jiraIssue in jiraIssueMultimap.keySet()) {
            def issueExists = my.jiraSite.existsIssue(jiraIssue.id)
            h3() { showTitle(jiraIssue, issueExists) }
            ol() {
                def changeLogs = jiraIssueMultimap.get(jiraIssue)
                for (def changeLog : changeLogs) {
                    li() {
                        showChangeLog(build, jiraIssue, changeLog, issueExists)
                    }
                }
            }
        }
    }

    if (!hadChanges) {
        text(_("No changes in any of the builds."))
    }
}

def showTitle(JiraIssue jiraIssue, issueExists) {
    def title = "${jiraIssue.id} - ${jiraIssue.title}"
    if (issueExists) {
        img(src: my.getIssueType(jiraIssue.id).icon, style: "width: 17px; height: 17px; margin-right: 5px")
        a(href: "${my.jiraSite.getUrl(jiraIssue.id)}", title)
    } else {
        text(title)
    }
}

def showChangeLog(build, jiraIssue, changeLog, issueExists) {
    if (issueExists) {
        text(stripJiraId(changeLog.msg, jiraIssue.id))
    } else {
        text(changeLog.msg)
    }
    text(" - ")
    showDetailLink(jiraIssue, changeLog)
    if (changeLog.parent.build != build) {
        text(" (")
        showBuild(changeLog.parent.build)
        text(")")
    }
}

def showDetailLink(JiraIssue jiraIssue, ChangeLogSet.Entry changeLog) {
    def build = changeLog.parent.build
    def browser = build.project.scm.effectiveBrowser
    if (browser?.getChangeSetLink(changeLog)) {
        a(href: browser.getChangeSetLink(changeLog), _("detail"))
    } else {
        a(href: "${build.absoluteUrl}changes", _("detail"))
    }
}

def stripJiraId(message, jiraId) {
    if (message.startsWith(jiraId)) {
        return message.substring(jiraId.length(), message.length()).trim()
    }
}

def showBuild(build) {
    a(href: "${rootURL}/${build.project.url}") { text(build.project.displayName) }
    st.nbsp()
    a(href: "${rootURL}/${build.url}") {
        text(build.displayName)
    }
}