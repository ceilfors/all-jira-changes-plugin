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

package org.jenkinsci.plugins.alljirachanges;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultimap;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.plugins.jira.JiraIssue;
import hudson.plugins.jira.JiraSite;
import hudson.scm.ChangeLogSet;
import org.jenkinsci.plugins.all_changes.ChangesAggregator;
import org.jvnet.localizer.LocaleProvider;

import javax.xml.rpc.ServiceException;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

import static org.jenkinsci.plugins.alljirachanges.Messages.*;

/**
 * <tt>Action</tt> that grabs all changes from a build from <tt>ChangesAggregator</tt>s and merge
 * the change logs with the JIRA information.
 *
 * @author ceilfors
 */
public class AllJiraChangesAction implements Action {

    private AbstractProject<?, ?> project;

    private transient List<ChangesAggregator> aggregators;

    public AllJiraChangesAction(AbstractProject<?, ?> project) {
        this.project = project;
    }

    /**
     * Returns a <tt>Multimap</tt> of <tt>JiraIssue</tt> to changeLog. The changeLogs are calculated
     * by using the <tt>ChangesAggregator</tt>s.
     *
     * @param build the build
     * @return the Multimap extracted out from the build
     */
    public Multimap<JiraIssue, ChangeLogSet.Entry> getAllJiraChanges(AbstractBuild build) {
        Set<AbstractBuild> builds = getContributingBuilds(build);

        Multimap<JiraIssue, ChangeLogSet.Entry> jiraIssueMultimap = TreeMultimap.create(
                new Comparator<JiraIssue>() {
                    public int compare(JiraIssue o1, JiraIssue o2) {
                        return o1.id.compareTo(o2.id);
                    }
                }, new Comparator<ChangeLogSet.Entry>() {
                    public int compare(ChangeLogSet.Entry o1, ChangeLogSet.Entry o2) {
                        return o1.getParent().build.getNumber() < o2.getParent().build.getNumber() ? -1 :
                                o1.getParent().build.getNumber() > o2.getParent().build.getNumber() ? 1 :
                                        o1.getTimestamp() < o2.getTimestamp() ? 1 : -1;
                    }
                }
        );

        for (AbstractBuild changedBuild : builds) {
            ChangeLogSet<ChangeLogSet.Entry> changeSet = changedBuild.getChangeSet();
            for (ChangeLogSet.Entry changeLog : changeSet) {
                JiraIssue jiraIssue = getJiraIssue(changeLog.getMsg());
                if (jiraIssue != null) {
                    jiraIssueMultimap.put(jiraIssue, changeLog);
                }
            }
        }

        return jiraIssueMultimap;
    }

    /**
     * Grabs the <tt>JiraIssue</tt> object from the specified change log message. This method will be extracting
     * out the JIRA id by using the pattern from the configured Jira plugin. <br />
     * <br />
     * If the particular JIRA issue can't be found anymore in JIRA, this method will return <tt>JiraIssue</tt> with the
     * extracted id with title: "N/A". <br />
     * <br />
     * If the changeLog specified doesn't match the pattern configured, this method will return <tt>JiraIssue</tt>
     * with id: "Not specified", title: "N/A".
     *
     * @param message the message which prefixed with the JIRA id
     * @return the <tt>JiraIssue</tt> object
     */
    private JiraIssue getJiraIssue(String message) {
        JiraSite jiraSite = getJiraSite();
        Matcher matcher = jiraSite.getIssuePattern().matcher(message);
        if (matcher.find()) {
            try {
                String jiraId = matcher.group(1);
                JiraIssue issue = jiraSite.getIssue(jiraId);
                if (issue != null) {
                    return issue;
                } else {
                    // Probably issue has been deleted, constructing manually without title
                    return new JiraIssue(jiraId, AllJiraChangesAction_notFound_title());
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ServiceException e) {
                throw new RuntimeException(e);
            }
        }
        return new JiraIssue(AllJiraChangesAction_notSpecified_id(), AllJiraChangesAction_notSpecified_title());
    }

    /**
     * Uses all ChangesAggregators to calculate the contributing builds. This method logic is the same with the one in
     * {@link org.jenkinsci.plugins.all_changes.AllChangesAction AllChangesAction}.
     *
     * @return all changes which contribute to the given build
     */
    private Set<AbstractBuild> getContributingBuilds(AbstractBuild build) {
        if (aggregators == null) {
            aggregators = ImmutableList.copyOf(ChangesAggregator.all());
        }
        Set<AbstractBuild> builds = Sets.newHashSet();
        builds.add(build);
        int size;
        do {
            size = builds.size();
            Set<AbstractBuild> newBuilds = Sets.newHashSet();
            for (ChangesAggregator aggregator : aggregators) {
                for (AbstractBuild depBuild : builds) {
                    newBuilds.addAll(aggregator.aggregateBuildsWithChanges(depBuild));
                }
            }
            builds.addAll(newBuilds);
        } while (size < builds.size());
        return builds;
    }

    /**
     * Returns the date format to be used for this plugin.
     * @return the date format
     */
    public DateFormat getDateFormat() {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM, LocaleProvider.getLocale());
    }

    public AbstractProject<?, ?> getProject() {
        return project;
    }

    public JiraSite getJiraSite() {
        return JiraSite.get(project);
    }

    public String getIconFileName() {
        return "notepad.png";
    }

    public String getDisplayName() {
        return Messages.AllJiraChangesAction_displayName();
    }

    public String getUrlName() {
        return "all-jira-changes";
    }
}
