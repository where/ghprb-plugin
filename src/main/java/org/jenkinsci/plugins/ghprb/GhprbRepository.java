package org.jenkinsci.plugins.ghprb;

import hudson.model.AbstractBuild;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.eclipse.egit.github.core.CommitComment;
import org.eclipse.egit.github.core.CommitStatus;
import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.RepositoryHook;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.event.IssueCommentPayload;
import org.eclipse.egit.github.core.event.PullRequestPayload;
import org.eclipse.egit.github.core.service.CommitService;
import org.eclipse.egit.github.core.service.PullRequestService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.kohsuke.github.GHEvent;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbRepository {
	private static final Logger logger = Logger.getLogger(GhprbRepository.class.getName());
	private final String repoName;
	private final String repoUser;

	private Map<Integer,GhprbPullRequest> pulls;

	private Repository repo;
	private Ghprb ml;
	private final PullRequestService pullService;
	private RepositoryId repoId;
	private final RepositoryService repoService;

	
	

	public GhprbRepository(String user,
	                 String repository,
	                 Ghprb helper,
	                 Map<Integer,GhprbPullRequest> pulls){
		repoName = repository;
		repoUser = user;
		this.ml = helper;
		this.pulls = pulls;
		repoService = new RepositoryService(ml.getGitHub().getClient());
		pullService = new PullRequestService(ml.getGitHub().getClient());
	}

	public void init(){
		checkState();
		for(GhprbPullRequest pull : pulls.values()){
			pull.init(ml,this);
		}
	}

	private boolean checkState(){
		if(repo == null){
			try {			
				repo = repoService.getRepository(repoUser, repoName);
			} catch (IOException ex) {
				logger.log(Level.SEVERE, "Could not retrieve repo named " + repoUser + "/"+repoName + " (Do you have properly set 'GitHub project' field in job configuration?)", ex);
				return false;
			}
		}
		return true;
	}
	
	public Repository getRepository(){
		checkState();
		return repo;
	}

	public void check(){
		if(!checkState()) return;

		List<PullRequest> prs;
		try {
			prs = pullService.getPullRequests(repoId, "open");
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Could not retrieve pull requests.", ex);
			return;
		}
		Set<Integer> closedPulls = new HashSet<Integer>(pulls.keySet());

		for(PullRequest pr : prs){
			
			check(pr);
			closedPulls.remove(pr.getNumber());
		}

		removeClosed(closedPulls, pulls);
	}

	private void check(PullRequest pr){
			Integer id = pr.getNumber();
			GhprbPullRequest pull;
			if(pulls.containsKey(id)){
				pull = pulls.get(id);
			}else{
				pull = new GhprbPullRequest(pr, ml, this);
				pulls.put(id, pull);
			}
			pull.check(pr);
	}

	private void removeClosed(Set<Integer> closedPulls, Map<Integer,GhprbPullRequest> pulls) {
		if(closedPulls.isEmpty()) return;

		for(Integer id : closedPulls){
			pulls.remove(id);
		}
	}

	public void createCommitStatus(AbstractBuild<?,?> build, String state, String message, int id){
		String sha1 = build.getCause(GhprbCause.class).getCommit();
		createCommitStatus(sha1, state, Jenkins.getInstance().getRootUrl() + build.getUrl(), message, id);
	}

	public void createCommitStatus(String sha1, String state, String url, String message, int id) {
		logger.log(Level.INFO, "Setting status of {0} to {1} with url {2} and message: {3}", new Object[]{sha1, state, url, message});
		CommitService commitService = new CommitService(ml.getGitHub().getClient());
		try {
			CommitStatus status = new CommitStatus();
			status.setState(state);
			status.setDescription(message);
			status.setUrl(url);
			commitService.createStatus(repo, sha1, status);
		} catch (IOException ex) {
			if(GhprbTrigger.getDscp().getUseComments()){
				logger.log(Level.INFO, "Could not update commit status of the Pull Request on Github. Trying to send comment.", ex);
				addComment(id, message);
			}else{
				logger.log(Level.SEVERE, "Could not update commit status of the Pull Request on Github.", ex);
			}
		}
	}

	public String getName() {
		return repoName;
	}

	public void addComment(int id, String comment) {
		try {
			CommitComment commitComment = new CommitComment();
			commitComment.setBody(comment);
			pullService.createComment(repo, id, commitComment);
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Couldn't add comment to pullrequest #" + id + ": '" + comment + "'", ex);
		}
	}

	public void closePullRequest(int id) {
		try {
			PullRequest pull = pullService.getPullRequest(repo, id);
			pull.setState("closed");
			pullService.editPullRequest(repo, pull);
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Couldn't close the pullrequest #" + id + ": '", ex);
		}
	}

	public String getRepoUrl(){
		return ml.getGitHubServer()+"/"+repoUser+"/"+repoName;
	}


	private static final EnumSet<GHEvent> EVENTS = EnumSet.of(GHEvent.ISSUE_COMMENT, GHEvent.PULL_REQUEST);
	private boolean hookExist() throws IOException{
		for(RepositoryHook h : repoService.getHooks(repo)){
			if(!"web".equals(h.getName())) continue;
			//System.out.println("  "+h.getEvents());
			//if(!EVENTS.equals(h.getEvents())) continue;
			if(!ml.getHookUrl().equals(h.getConfig().get("url"))) continue;
			return true;
		}
		return false;
	}

	public boolean createHook(){
		try{
			if(hookExist()) return true;
			RepositoryHook hook = new RepositoryHook();
			hook.setName("PullRequestBuilderHook");
			
			repoService.createHook(repo, hook);
			//repo.createWebHook(new URL(ml.getHookUrl()),EVENTS);
			
			return true;
		}catch(IOException ex){
			logger.log(
					Level.SEVERE,
					"Couldn't create web hook for repository "+
					repoName+
					". Does the user (from global configuration) have admin rights to the repository?",
					ex);
			return false;
		}
	}

	public org.eclipse.egit.github.core.PullRequest getPullRequest(int id) throws IOException{
		return pullService.getPullRequest(repo,id);
	}

	void onIssueCommentHook(IssueCommentPayload issueComment) {
		int id = issueComment.getIssue().getNumber();
		if(logger.isLoggable(Level.FINER)){
			logger.log(
					Level.FINER,
					"Comment on issue #{0}: '{1}'",
					new Object[]{id,issueComment.getComment().getBody()});
		}
		if(!"created".equals(issueComment.getAction())) return;
		GhprbPullRequest pull = pulls.get(id);
		if(pull == null){
			if(logger.isLoggable(Level.FINER)){
				logger.log(Level.FINER, "Pull request #{0} desn't exist", id);
			}
			return;
		}
		pull.check(issueComment.getComment());
		GhprbTrigger.getDscp().save();
	}

	void onPullRequestHook(PullRequestPayload pr) {
		if("opened".equals(pr.getAction()) || "reopened".equals(pr.getAction())){
			GhprbPullRequest pull = pulls.get(pr.getNumber());
			if(pull == null){
				pull = new GhprbPullRequest(pr.getPullRequest(), ml, this);
				pulls.put(pr.getNumber(), pull);
			}
			pull.check(pr.getPullRequest());
		}else if("synchronize".equals(pr.getAction())){
			GhprbPullRequest pull = pulls.get(pr.getNumber());
			if(pull == null){
				logger.log(Level.SEVERE, "Pull Request #{0} doesn't exist", pr.getNumber());
				return;
			}
			pull.check(pr.getPullRequest());
		}else if("closed".equals(pr.getAction())){
			pulls.remove(pr.getNumber());
		}else{
			logger.log(Level.WARNING, "Unknown Pull Request hook action: {0}", pr.getAction());
		}
		GhprbTrigger.getDscp().save();
	}
}
