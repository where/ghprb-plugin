package org.jenkinsci.plugins.ghprb;

import org.eclipse.egit.github.core.PullRequest;

import hudson.model.Cause;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbCause extends Cause{
	private final String commit;
	private final PullRequest pull;
	private final boolean merged;
	private final String targetBranch;

	public GhprbCause(String commit, PullRequest pull, boolean merged, String targetBranch){
		this.commit = commit;
		this.pull = pull;
		this.merged = merged;
		this.targetBranch = targetBranch;
	}

	@Override
	public String getShortDescription() {
		return "Github pull request #" + pull + " of commit " + commit + (merged? " automatically merged." : ".");
	}

	public String getCommit() {
		return commit;
	}
	
	public boolean isMerged() {
		return merged;
	}

	public PullRequest getPullID(){
		return pull;
	}

	public String getTargetBranch() {
		return targetBranch;
	}
}
