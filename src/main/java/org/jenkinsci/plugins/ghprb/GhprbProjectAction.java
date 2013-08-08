package org.jenkinsci.plugins.ghprb;

import hudson.model.ProminentProjectAction;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.egit.github.core.event.IssueCommentPayload;
import org.eclipse.egit.github.core.event.PullRequestPayload;
import org.kohsuke.stapler.StaplerRequest;

import com.google.gson.Gson;

/**
 * @author janinko
 */
@Deprecated
public class GhprbProjectAction implements ProminentProjectAction{
	private static final Logger logger = Logger.getLogger(GhprbProjectAction.class.getName());
	static final String URL = "ghprbhook";
	private GhprbGitHub gh;
	private GhprbRepository repo;

	public GhprbProjectAction(GhprbTrigger trigger){
		repo = trigger.getGhprb().getRepository();
		gh = trigger.getGhprb().getGitHub();
	}

	public String getIconFileName() {
		return null;
	}

	public String getDisplayName() {
		return null;
	}

	public String getUrlName() {
		return URL;
	}

	public void doIndex(StaplerRequest req) {
		String event = req.getHeader("X-GitHub-Event");
		String payload = req.getParameter("payload");
		if(payload == null){
			logger.log(Level.SEVERE, "Request doesn't contain payload.");
			return;
		}

		logger.log(Level.INFO, "Got payload event: {0}", event);
		Gson gson = new Gson();
		if("issue_comment".equals(event)){
			IssueCommentPayload issueComment = gson.fromJson(payload,IssueCommentPayload.class);
			repo.onIssueCommentHook(issueComment);
		}else if("pull_request".equals(event)) {
			PullRequestPayload pr = gson.fromJson(payload,PullRequestPayload.class);
			repo.onPullRequestHook(pr);
		}else{
			logger.log(Level.WARNING, "Request not known");
		}
		
	}
}
