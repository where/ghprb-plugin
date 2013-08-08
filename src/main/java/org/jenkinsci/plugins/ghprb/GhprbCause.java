package org.jenkinsci.plugins.ghprb;

import hudson.model.Cause;

/**
 * @author Honza Brázdil <jbrazdil@redhat.com>
 */
public class GhprbCause extends Cause{
	private final String commit;
	private final int pullID;
	private final boolean mergable;
	private final String targetBranchName;
	private String description;
	private String title;

	public GhprbCause(String commit, int pullID, boolean mergable, String targetBranchName, String description, String title){
		this.commit = commit;
		this.pullID = pullID;
		this.mergable = mergable;
		this.targetBranchName = targetBranchName;
		this.description = description;
		this.title = title;
	}

	@Override
	public String getShortDescription() {
		return "Github pull request #" + pullID + " of commit " + commit + (mergable? " automatically merged." : ".");
	}
	
	public String getPullDescription(){
		if(description==null){
			return "";
		}
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

	public String getTargetBranchName() {
		return targetBranchName;
	}

	public String getPullTitle() {
		return title;
	}

	public String getAuthorEmail() {
		return authorEmail;
	}

	/**
	 * Returns the title of the cause, not null.
	 * @return
	 */
	public String getTitle() {
		return title != null ? title : "";
	}

	/**
	 * Returns at most the first 30 characters of the title, or 
	 * @return
	 */
	public String getAbbreviatedTitle() {
		return StringUtils.abbreviate(getTitle(), 30);
	}
	
	
}
