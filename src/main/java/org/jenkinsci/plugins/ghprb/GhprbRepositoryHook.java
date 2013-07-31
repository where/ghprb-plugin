package org.jenkinsci.plugins.ghprb;

import java.util.List;

import org.eclipse.egit.github.core.RepositoryHook;

public class GhprbRepositoryHook extends RepositoryHook{
	
	/**
	 *  Any git push to a Repository.
	 */
	public static final String PUSH = "push"; //$NON-NLS-1$

	/**
	 * Any time an Issue is opened or closed.
	 */
	public static final String ISSUES = "issues"; //$NON-NLS-1$

	/**
	 * Any time an Issue is commented on.
	 */
	public static final String ISSUE_COMMENT = "issue_comment"; //$NON-NLS-1$

	/**
	 * Any time a Commit is commented on.
	 */
	public static final String COMMIT_COMMENT = "commit_comment"; //$NON-NLS-1$

	/**
	 * Any time a Pull Request is opened, closed, or synchronized
	 */
	public static final String PULL_REQUEST = "pull_request"; //$NON-NLS-1$

	/**
	 * Any time a Commit is commented on while inside a Pull Request review
	 */
	public static final String PULL_REQUEST_REVIEW_COMMENT = "pull_request_review_comment"; //$NON-NLS-1$

	/**
	 * Any time a Wiki page is updated.
	 */
	public static final String GOLLUM = "gollum"; //$NON-NLS-1$

	/**
	 * Any time a User watches the Repository.
	 */
	public static final String WATCH = "watch"; //$NON-NLS-1$

	/**
	 * Any time a Download is added to the Repository.
	 */
	public static final String DOWNLOAD = "download"; //$NON-NLS-1$

	/**
	 * Any time a Download is added to the Repository.
	 */
	public static final String FORK = "fork"; //$NON-NLS-1$

	/**
	 * Any time a patch is applied to the Repository from the Fork Queue.
	 */
	public static final String FORK_APPLY = "fork_apply"; //$NON-NLS-1$

	/**
	 * Any time a User is added as a collaborator to a non-Organization Repository.
	 */
	public static final String MEMBER = "member"; //$NON-NLS-1$

	/**
	 * Any time a Repository changes from private to public.
	 */
	public static final String PUBLIC = "public"; //$NON-NLS-1$

	/**
	 * Any time a team is added or modified on a Repository.
	 */
	public static final String TEAM_ADD = "team_add"; //$NON-NLS-1$

	/**
	 * Any time a Repository has a status update from the API
	 */
	public static final String STATUS = "status"; //$NON-NLS-1$
	
	private List<String> events;

	public List<String> getEvents() {
		return events;
	}

	public void setEvents(List<String> events) {
		this.events = events;
	}

}
