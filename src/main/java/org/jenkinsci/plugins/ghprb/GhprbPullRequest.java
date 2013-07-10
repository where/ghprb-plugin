package org.jenkinsci.plugins.ghprb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.egit.github.core.Comment;
import org.eclipse.egit.github.core.CommitComment;
import org.eclipse.egit.github.core.CommitStatus;
import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.service.CommitService;
import org.eclipse.egit.github.core.service.IssueService;
import org.eclipse.egit.github.core.service.PullRequestService;


/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbPullRequest{
	private static final Logger logger = Logger.getLogger(GhprbPullRequest.class.getName());
	private final int id;
	private final String author;
	private Date updated;
	private String head;
	private boolean mergeable;
	private String reponame;
	private String target;
	private final PullRequest pull;

	private boolean shouldRun = false;
	private boolean accepted = false;
	private boolean checkStatusClose =false;
	@Deprecated private transient boolean askedForApproval; // TODO: remove

	private transient Ghprb ml;
	private transient GhprbRepository repo;
	private final PullRequestService pullService;

	GhprbPullRequest(PullRequest pr, Ghprb helper, GhprbRepository repo) {
		pull=pr;
		id = pr.getNumber();
		updated = pr.getUpdatedAt();
		head = pr.getHead().getSha();
		author = pr.getUser().getLogin();
		reponame = repo.getName();
		target = pr.getBase().getRef();

		this.ml = helper;
		this.repo = repo;

		pullService = new PullRequestService(ml.getGitHub().getClient());
		
		if(helper.isWhitelisted(author)){
			accepted = true;
			shouldRun = true;
		}else{
			logger.log(Level.INFO, "Author of #{0} {1} on {2} not in whitelist!", new Object[]{id, author, reponame});
			repo.addComment(id, GhprbTrigger.getDscp().getRequestForTestingPhrase());
		}

		logger.log(Level.INFO, "Created pull request #{0} on {1} by {2} updated at: {3} SHA: {4}", new Object[]{id, reponame, author, updated, head});
	}

	public void init(Ghprb helper, GhprbRepository repo) {
		this.ml = helper;
		this.repo = repo;
		if(reponame == null) reponame = repo.getName(); // If this instance was created before v1.8, it can be null.
	}

	public void check(PullRequest pr){
		if(target == null) target = pr.getBase().getRef(); // If this instance was created before target was introduced (before v1.8), it can be null.

		if(isUpdated(pr)){
			logger.log(Level.INFO, "Pull request builder: pr #{0} was updated on {1} at {2}", new Object[]{id, reponame, updated});

			int commentsChecked = checkComments(pr);
			boolean newCommit   = checkCommit(pr.getHead().getSha());

			if(!newCommit && commentsChecked == 0){
				logger.log(Level.INFO, "Pull request was updated on repo {0} but there aren''t any new comments nor commits - that may mean that commit status was updated.", reponame);
			}
			updated = pr.getUpdatedAt();
		}
	
		if(shouldRun){
			checkMergeable(pr);
			build();
		}
	}

	public void check(Comment comment) {
		try {
			checkComment(comment);
			updated = comment.getUpdatedAt();
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "Couldn't check comment #" + comment.getId(), ex);
			return;
		}
		if (shouldRun) {
			build();
		}
	}

	private boolean isUpdated(PullRequest pr){
		boolean ret = false;
		ret = ret || updated.compareTo(pr.getUpdatedAt()) < 0;
		ret = ret || !pr.getHead().getSha().equals(head);

		return ret;
	}

	private void build(){
		shouldRun = false;
		String message = ml.getBuilds().build(this);

		repo.createCommitStatus(head, CommitStatus.STATE_PENDING, null, message,id);

		logger.log(Level.INFO, message);
	}

	// returns false if no new commit
	private boolean checkCommit(String sha){
		if(head.equals(sha)) return false;

		if(logger.isLoggable(Level.FINE)){
			logger.log(Level.FINE, "New commit. Sha: {0} => {1}", new Object[]{head, sha});
		}

		head = sha;
		if(accepted){
			shouldRun = true;
		}
		return true;
	}

	private void checkComment(Comment comment) throws IOException {
		logger.fine("Checking comment: "+comment.getBody());
		String sender = comment.getUser().getLogin();
		String body = comment.getBody();

		// add to whitelist
		if (ml.isWhitelistPhrase(body) && ml.isAdmin(sender)){
			if(!ml.isWhitelisted(author)) {
				ml.addWhitelist(author);
			}
			accepted = true;
			shouldRun = true;
		}
		
		//ok to close pull request after test
		
		if(ml.isOktoMergeAfterTest(body) && ml.isAdmin(sender)){
			checkStatusClose=true;
			if(check_PR_test_passed()){
				merge_PR();
			}
		}

		// ok to test
		if(ml.isOktotestPhrase(body) && ml.isAdmin(sender)){
			accepted = true;
			shouldRun = true;
		}

		// test this please
		if (ml.isRetestPhrase(body)){
			if(ml.isAdmin(sender)){
				shouldRun = true;
			}else if(accepted && ml.isWhitelisted(sender) ){
				shouldRun = true;
			}
		}
	}

	private void merge_PR() {
		if(this.mergeable){
			try {
				pullService.merge(repo.getRepoObject(), pull.getNumber(), "Automatically merged after pull request tests passed");
			} catch (IOException e) {
				logger.severe("Unable to merge pull request after tests have passed because of error: "+e.getMessage());
			}
		}
		else{
			repo.addComment(pull.getNumber(), "Unable to automatically merge because pull request is not mergable");
		}
	}

	private boolean check_PR_test_passed() {
		CommitService commitService = new CommitService(ml.getGitHub().getClient());
		try {
			List<CommitStatus> statuses = commitService.getStatuses(repo.getRepoObject(), head);
			if(statuses.get(0).getState().equals("success")){
				logger.fine("Got a success for CommitStatus url: "+ statuses.get(0).getUrl());
				return true;
			}
			logger.fine("Did not get a success for CommitStatus url: "+ statuses.get(0).getUrl());
		} catch (IOException e) {
			logger.severe("Could not get the status of the commit to see if the tests have passed: "+e.getMessage());
		}
		return false;
	}

	private int checkComments(PullRequest pr) {
		IssueService issueService = new IssueService(ml.getGitHub().getClient());
		
		int count = 0;
		try {
			for (Comment comment : issueService.getComments(repo.getRepository(), pr.getNumber())) {
				if (updated.compareTo(comment.getUpdatedAt()) < 0) {
					count++;
					try {
						checkComment(comment);
					} catch (IOException ex) {
						logger.log(Level.SEVERE, "Couldn't check comment #" + comment.getId(), ex);
					}
				}
			}
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Couldn't obtain comments.", e);
		}
		logger.fine("Checked "+count+"comments for pull request "+pr.getNumber());
		return count;
	}

	private void checkMergeable(PullRequest pr) {
		try {
			int r=5;
			while(!pr.isMergeable() && r-->0){
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ex) {
					break;
				}
				pr = repo.getPullRequest(id);
			}
			mergeable = pr.isMergeable();
		} catch (IOException e) {
			mergeable = false;
			logger.log(Level.SEVERE, "Couldn't obtain mergeable status.", e);
		}
	}

	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof GhprbPullRequest)) return false;

		GhprbPullRequest o = (GhprbPullRequest) obj;
		return o.id == id;
	}

	@Override
	public int hashCode() {
		int hash = 7;
		hash = 89 * hash + this.id;
		return hash;
	}

	public int getId() {
		return id;
	}

	public String getHead() {
		return head;
	}

	public boolean isMergeable() {
		return mergeable;
	}

	public String getTarget(){
		return target;
	}

	public PullRequest getPullRequestObject() {
		return pull;
	}
}
