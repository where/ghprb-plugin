package org.jenkinsci.plugins.ghprb;

import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.Cause;
import hudson.model.queue.QueueTaskFuture;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

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

		GhprbCause cause = new GhprbCause(pr.getHead(), pr.getId(), pr.isMergeable(), pr.getTarget(), pr.getPullRequestObject().getBody(),pr.getPullRequestObject().getTitle());

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

		repo.createCommitStatus(build, "pending", (c.isMergable() ? "Merged build started." : "Build started."),(int)c.getPullID());
		try {
			build.setDescription("<a href=\"" + repo.getRepoUrl()+"/pull/"+c.getPullID()+"\">Pull request #"+c.getPullID()+"</a>");
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Can't update build description", ex);
		}
		
		String msg="A Jenkins Build to test this PR has been started.";
		
		msg = msg + " " + Jenkins.getInstance().getRootUrl() + build.getUrl();
		
		repo.addComment(c.getPullID(),msg);
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
		repo.createCommitStatus(build, state, (c.isMergable() ? "Merged build finished." : "Build finished."),c.getPullID());

		String msg="The Jenkins build to test the PR has been complete\n";
		if (state == CommitStatus.STATE_SUCCESS) {
			msg = GhprbTrigger.getDscp().getMsgSuccess();
		} else {
			msg = GhprbTrigger.getDscp().getMsgFailure();
		}

			msg = msg + "\nRefer to this link for build results: " + Jenkins.getInstance().getRootUrl() + build.getUrl();
		

		repo.addComment(c.getPullID(),msg);
		
		// close failed pull request automatically
		if (state == CommitStatus.STATE_FAILURE && trigger.isAutoCloseFailedPullRequests()) {
			repo.closePullRequest(c.getPullID());
		}
	}
}
