package org.jenkinsci.plugins.ghprb;

import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import hudson.model.AbstractProject;
import hudson.security.ACL;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContextHolder;
import org.eclipse.egit.github.core.event.IssueCommentPayload;
import org.eclipse.egit.github.core.event.PullRequestPayload;
import org.kohsuke.github.GHRepository;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
@Extension
public class GhprbRootAction implements UnprotectedRootAction {
	private static final Logger logger = Logger.getLogger(GhprbRootAction.class.getName());
	static final String URL = "ghprbhook";
	
	public String getIconFileName() {
		return null;
	}

	public String getDisplayName() {
		return null;
	}

	public String getUrlName() {
		return URL;
	}

	public void doIndex(StaplerRequest req, StaplerResponse resp) {
		String event = req.getHeader("X-Github-Event");
		String payload = req.getParameter("payload");
		JsonObject repo = new JsonParser().parse(req.getParameter("repo")).getAsJsonObject();
		String repoName = repo.get("name").getAsString();
		
		if(payload == null){
			logger.log(Level.SEVERE, "Request doesn't contain payload.");
			return;
		}

		GhprbGitHub gh = GhprbTrigger.getDscp().getGitHub();

		logger.log(Level.INFO, "Got payload event: {0}", event);
		Gson gson = new Gson();
			if("issue_comment".equals(event)){
				IssueCommentPayload issueComment = gson.fromJson(payload, IssueCommentPayload.class);
				for(GhprbRepository gHrepo : getRepos(repoName)){
					gHrepo.onIssueCommentHook(issueComment);
				}
			}else if("pull_request".equals(event)) {
				PullRequestPayload pr = gson.fromJson(payload, PullRequestPayload.class);
				//for(GhprbRepository repo : getRepos(pr.getPullRequest().getRepository())){ // not working with github-api v1.40
				for(GhprbRepository gHrepo : getRepos(repoName)){ // WA until ^^ fixed
					gHrepo.onPullRequestHook(pr);
				}
			}else{
				logger.log(Level.WARNING, "Request not known");
			}
	
	}

	private Set<GhprbRepository> getRepos(GHRepository repo) throws IOException{
		return getRepos(repo.getOwner().getLogin() + "/" + repo.getName());
	}

	private Set<GhprbRepository> getRepos(String repo){
		HashSet<GhprbRepository> ret = new HashSet<GhprbRepository>();

		// We need this to get acces to list of repositories
		Authentication old = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(ACL.SYSTEM);

		try{
			for(AbstractProject<?,?> job : Jenkins.getInstance().getAllItems(AbstractProject.class)){
				GhprbTrigger trigger = job.getTrigger(GhprbTrigger.class);
				if(trigger == null) continue;
				GhprbRepository r = trigger.getGhprb().getRepository();
				if(repo.equals(r.getName())){
					ret.add(r);
				}
			}
		}finally{
			SecurityContextHolder.getContext().setAuthentication(old);
		}
		return ret;
	}
}
