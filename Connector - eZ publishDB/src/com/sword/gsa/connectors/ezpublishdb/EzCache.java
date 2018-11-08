package com.sword.gsa.connectors.ezpublishdb;

import java.util.List;

import sword.gsa.xmlfeeds.builder.acl.Group;
import sword.gsa.xmlfeeds.builder.acl.User;

import com.sword.gsa.spis.scs.commons.acl.cache.FeedableGroupCache;

public class EzCache extends FeedableGroupCache {

	private static final long serialVersionUID = 1L;

	final List<Group> allGroups;
	final List<User> allUsers;

	public EzCache(final List<Group> allGroups, final List<User> allUsers) {
		this.allGroups = allGroups;
		this.allUsers = allUsers;
	}

	@Override
	public Iterable<Group> getGroups() {
		return allGroups;
	}

	@Override
	public Iterable<User> getUsers() {
		return allUsers;
	}

	@Override
	public String getCacheInfo() {
		return String.format("%d groups and %d users in cache", allGroups.size(), allUsers.size());
	}

}
