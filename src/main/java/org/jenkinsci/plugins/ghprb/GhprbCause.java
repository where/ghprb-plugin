package org.jenkinsci.plugins.ghprb;

import hudson.model.Cause;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbCause extends Cause{
	private final String commit;
	private final int pullID;
	private final boolean mergable;
	private final String targetBranch;
	private String description;
	private String title;

	public GhprbCause(String commit, int pullID, boolean mergable, String targetBranch, String description, String title){
		this.commit = commit;
		this.pullID = pullID;
		this.mergable = mergable;
		this.targetBranch = targetBranch;
		this.description = description;
		this.title = title;
	}

	@Override
	public String getShortDescription() {
		return "Github pull request #" + pullID + " of commit " + commit + (mergable? " automatically merged." : ".");
	}
	
	public String getPullDescription(){
		return description;
	}

	public String getCommit() {
		return commit;
	}
	
	public boolean isMergable() {
		return mergable;
	}

	public int getPullID(){
		return pullID;
	}

	public String getTargetBranch() {
		return targetBranch;
	}

	public String getPullTitle() {
		return title;
	}
}
