package org.jenkinsci.plugins.ghprb;

import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.queue.QueueTaskFuture;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.egit.github.core.CommitStatus;

/**
 * @author janinko
 */
public class GhprbBuilds {
	private static final Logger logger = Logger.getLogger(GhprbBuilds.class.getName());
	private GhprbTrigger trigger;
	private GhprbRepository repo;

	public GhprbBuilds(GhprbTrigger trigger, GhprbRepository repo){
		this.trigger = trigger;
		this.repo = repo;
	}

	public String build(GhprbPullRequest pr) {
		StringBuilder sb = new StringBuilder();
		if(cancelBuild(pr.getId())){
			sb.append("Previous build stopped.");
		}

		if(pr.isMergeable()){
			sb.append(" Merged build triggered.");
		}else{
			sb.append(" Build triggered.");
		}

		GhprbCause cause = new GhprbCause(pr.getHead(), pr.getPullRequestObject(), pr.isMergeable(), pr.getTarget());

		QueueTaskFuture<?> build = trigger.startJob(cause);
		if(build == null){
			logger.log(Level.SEVERE, "Job didn't started");
		}
		return sb.toString();
	}

	private boolean cancelBuild(int id) {
		return false;
	}

	private GhprbCause getCause(AbstractBuild build){
		Cause cause = build.getCause(GhprbCause.class);
		if(cause == null || (!(cause instanceof GhprbCause))) return null;
		return (GhprbCause) cause;
	}

	public void onStarted(AbstractBuild build) {
		GhprbCause c = getCause(build);
		if(c == null) return;

		repo.createCommitStatus(build, "pending", (c.isMerged() ? "Merged build started." : "Build started."),(int)c.getPullID().getId());
		try {
			build.setDescription("<a href=\"" + repo.getRepoUrl()+"/pull/"+c.getPullID().getNumber()+"\">Pull request #"+c.getPullID().getNumber()+"</a>");
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Can't update build description", ex);
		}
		
		String publishedURL = GhprbTrigger.getDscp().getPublishedURL();
		String msg="A Jenkins Build to test this PR has been started.";
		
		if (publishedURL != null && !publishedURL.isEmpty()) {
			msg = msg + " " + publishedURL + build.getUrl();
		}
		repo.addComment(c.getPullID().getNumber(),msg);
	}

	public void onCompleted(AbstractBuild build) {
		GhprbCause c = getCause(build);
		
		if(c == null) return;

		String state;
		if (build.getResult() == Result.SUCCESS) {
			state = CommitStatus.STATE_SUCCESS;
		} else if (build.getResult() == Result.UNSTABLE){
			state = GhprbTrigger.getDscp().getUnstableAs();
		} else {
			state = CommitStatus.STATE_FAILURE;
		}
		repo.createCommitStatus(build, state, (c.isMerged() ? "Merged build finished." : "Build finished."),c.getPullID().getNumber() );

		String publishedURL = GhprbTrigger.getDscp().getPublishedURL();
		String msg="The Jenkins build to test the PR has been complete\n";
		if (state == CommitStatus.STATE_SUCCESS) {
			msg = GhprbTrigger.getDscp().getMsgSuccess();
		} else {
			msg = GhprbTrigger.getDscp().getMsgFailure();
		}
		if (publishedURL != null && !publishedURL.isEmpty()) {
			
			msg = msg + "\nRefer to this link for build results: " + publishedURL + build.getUrl();
		}

		repo.addComment(c.getPullID().getNumber(),msg);
		
		// close failed pull request automatically
		if (state == CommitStatus.STATE_FAILURE && trigger.isAutoCloseFailedPullRequests()) {
			repo.closePullRequest(c.getPullID().getNumber());
		}
	}
}
