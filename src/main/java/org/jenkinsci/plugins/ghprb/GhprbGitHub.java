package org.jenkinsci.plugins.ghprb;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.OrganizationService;


/**
 * @author janinko
 */
public class GhprbGitHub {
	private static final Logger logger = Logger.getLogger(GhprbGitHub.class.getName());
	
	private GitHubClient client ;

	private void connect(){
		String accessToken    = GhprbTrigger.getDscp().getAccessToken();
		String serverAPIUrl   = GhprbTrigger.getDscp().getServerAPIUrl();
		Boolean useEnterprise = GhprbTrigger.getDscp().getUseEnterprise();
		String username       = GhprbTrigger.getDscp().getUsername();
		String password       = GhprbTrigger.getDscp().getPassword();
		
		if(useEnterprise){
			client = new GitHubClient(serverAPIUrl);
		}
		else{
			client = new GitHubClient();
		}
		if(accessToken != null && !accessToken.isEmpty()) {
			client = new GitHubClient(serverAPIUrl);
			client.setOAuth2Token(accessToken);

		} else {
			client.setCredentials(username, password);

		}
	}

	public GitHubClient getClient() {
		if(client == null){
			connect();
		}
		return client;
	}

	public boolean isUserMemberOfOrganization(String organization, String member){
		OrganizationService org = new OrganizationService(this.getClient());
		try {	
			List<User> members = org.getMembers(organization);
			for(User user : members){
				if(user.getLogin().equals(member)){
					return true;
				}
			}
		} catch (IOException ex) {
			logger.log(Level.SEVERE, null, ex);
			return false;
		}
		return false;
	}
}
