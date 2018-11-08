package com.sword.gsa.spis.scs.commons.connector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.log4j.Logger;

import sword.common.utils.dates.DateUtils;
import sword.gsa.xmlfeeds.builder.acl.Group;
import sword.gsa.xmlfeeds.builder.acl.Principal;
import sword.gsa.xmlfeeds.builder.acl.User;
import sword.gsa.xmlfeeds.builder.streamed.MembershipXMLOutputStream;
import sword.gsa.xmlfeeds.builder.streamed.SendFeed;

import com.sword.gsa.spis.scs.commons.acl.cache.FailedCache;
import com.sword.gsa.spis.scs.commons.acl.cache.FeedableGroupCache;
import com.sword.gsa.spis.scs.commons.acl.cache.GroupCache;
import com.sword.gsa.spis.scs.commons.acl.cache.NullCache;
import com.sword.gsa.spis.scs.commons.acl.cache.PseudoCache;
import com.sword.gsa.spis.scs.commons.acl.cache.UsersAsGroupsAlreadyResolved;
import com.sword.gsa.spis.scs.commons.connector.models.ICachableGroupRetriever;
import com.sword.gsa.spis.scs.gsaadmin.GSAInfo;
import com.sword.gsa.xmlfeeds.MindbreezeFeedPoster;

public final class GroupCacheLoader implements Runnable {

	private static final Logger LOG = Logger.getLogger(GroupCacheManager.class);

	private final Object waitLock = new Object();
	private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
	private final GSAInfo gsaInfo;
	private final ICachableGroupRetriever cgr;
	private final ClassLoader cl;
	private final String id;
	private final Path groupRetrieverCachePath;
	private final long cacheRefreshInterval;
	private final AtomicBoolean stop = new AtomicBoolean(false);
	private final AtomicBoolean isReloading = new AtomicBoolean(false);

	private GroupCache cache = new NullCache();
	private int errorCount = 0;

	public GroupCacheLoader(final GSAInfo gsaInfo, final ICachableGroupRetriever cgr, final String repositoryId, final Path groupRetrieverCachePath, final long cacheRefreshInterval) {
		this.gsaInfo = gsaInfo;
		this.cgr = cgr;
		this.cl = this.cgr.getClass().getClassLoader();
		id = repositoryId;
		this.groupRetrieverCachePath = groupRetrieverCachePath;
		this.cacheRefreshInterval = cacheRefreshInterval;
	}

	private final void setCache(final GroupCache cache) {
		cacheLock.writeLock().lock();
		try {
			this.cache = cache;
		} finally {
			cacheLock.writeLock().unlock();
		}
	}

	/**
	 * Returns cached data
	 *
	 * @throws Exception
	 *             If cache is outdated due to repeating error while trying to refresh it.
	 */
	public final GroupCache getCache() {
		GroupCache currentCache = null;
		cacheLock.readLock().lock();
		try {
			currentCache = cache;
		} finally {
			cacheLock.readLock().unlock();
		}
		return currentCache;
	}

	public boolean isReloading() {
		return isReloading.get();
	}

	@Override
	public void run() {
		
		Thread.currentThread().setName("Cache_" + id);
		LOG.info("Starting group membership cache loading for connector #" + id);
		
		final GroupCache nullCache = new NullCache();
		setCache(nullCache);
		
		try {

			final String gcFileName = String.format("gcser_%s.ser", id);
			{
				final Path instanceHomeDir = groupRetrieverCachePath.getParent();
				final Path oldLocation = instanceHomeDir.resolve(gcFileName);
				if (oldLocation.toFile().exists()) {
					try {
						Files.move(oldLocation, groupRetrieverCachePath.resolve(gcFileName), StandardCopyOption.REPLACE_EXISTING);
					} catch (IOException ignore) { }
				}
			}
			final File cacheFile = groupRetrieverCachePath.resolve(gcFileName).toFile();
			if (cacheFile.exists()) {
				LOG.info("Found serialized cache object for #" + id);
				try (CLAwareOIS ois = this.new CLAwareOIS(new FileInputStream(cacheFile), cl);) {
					final GroupCache oldCache = (GroupCache) ois.readObject();
					if (!(oldCache instanceof PseudoCache)) {
						setCache(oldCache);
						long now = System.currentTimeMillis();
						long cacheCreationTime = cacheFile.lastModified();
						long age = now - cacheCreationTime;
						if (age < cacheRefreshInterval) {
							LOG.info("Group cache is still valid for " + DateUtils.toReadableTimeSpan(cacheRefreshInterval - age));
							synchronized (waitLock) { waitLock.wait(cacheRefreshInterval - age); }
						}
					} else {
						LOG.info("Serialized cache object for #" + id + " is not valid");
					}
				} catch (final Exception e) {
					LOG.warn("Could not load cache from file: " + cacheFile.getAbsolutePath() + " ; generating new cache.", e);
				}
			}

			while (!stop.get()) {
				
				final GroupCache cacheBackup = getCache();
				GroupCache newCache = nullCache;
				try {
					isReloading.set(true);
					try {
						
						newCache = this.cgr.getNewCache();
						LOG.info("Obtained new cache: " + newCache.getCacheInfo());
						
						if (gsaInfo.is72orHigher && (newCache instanceof FeedableGroupCache)) {
							LOG.info("Feeding group membership to GSA");
							feedGroupMemberships((FeedableGroupCache)newCache, cacheBackup);
						} else if (gsaInfo.is72orHigher) {
							LOG.info("NOT feeding group membership to GSA because the connector does not support it");
						} else {
							LOG.info("NOT feeding group membership to GSA because the GSA does not support it");
						}

						// Save cache to file
						try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cacheFile));) {
							oos.writeObject(newCache);
							LOG.debug("Serialized new cache Object for rep #" + id);
						}
						
						errorCount = 0;
						setCache(newCache);
						LOG.info("Loaded new group membership cache for connector #" + id);
						
					} finally {
						isReloading.set(false);
					}
					
				} catch (Exception e) {
					newCache = new FailedCache(e);
					errorCount++;
					LOG.warn("Load cache failed for rep #" + id + ": ", e);
					if (errorCount > 6) {
						LOG.error("Too many errors for repository: " + id + "; aborting");
						setCache(newCache);
						break;
					} else {
						if (cacheBackup instanceof NullCache) setCache(newCache);
						else setCache(cacheBackup);
					}
					synchronized (waitLock) { waitLock.wait(errorCount * TimeUnit.MINUTES.toMillis(1)); }
				}

				if (!stop.get()) synchronized (waitLock) { waitLock.wait(cacheRefreshInterval); }

			}
			LOG.info("Group membership cache loader #" + id + " stopped successfully");
		} catch (final InterruptedException ignore) {}
		
	}

	private void feedGroupMemberships(FeedableGroupCache newCache, GroupCache cacheBackup) throws ReflectiveOperationException, IOException {
		
		List<Group> oldGroups = new ArrayList<>();
		if (cacheBackup instanceof FeedableGroupCache) 
			for (Group g: ((FeedableGroupCache)cacheBackup).getGroups()) 
				oldGroups.add(g);
		
		try (
				MindbreezeFeedPoster fp = new MindbreezeFeedPoster(gsaInfo.ssl); 
				MembershipXMLOutputStream mxos = new MembershipXMLOutputStream(groupRetrieverCachePath.toFile(), StandardCharsets.UTF_8, 100_000_000);
		) {
			
			int i = 0;
			for (Group g: newCache.getGroups()) {//Add groups
				i++;
				LOG.debug("Processing group " + i + ": " + g.principal);
				oldGroups.remove(g);
				Group g2 = membersToGroup(g);
				try {
					mxos.addMembership(g2);
				} catch (SendFeed e) {
					LOG.debug("Sending group membership feed to " + gsaInfo.defaultHost);
				}
			}
			
			i = 0;
			if (!oldGroups.isEmpty()) {
				for (Group g: oldGroups) {//Clear deleted groups from GSA
					i++;
					LOG.debug("Processing deleted group " + i + ": " + g.principal);
					try {
						mxos.addMembership(new Group(g.principal, g.namespace));
					} catch (SendFeed e) {
						LOG.debug("Sending group membership feed to " + gsaInfo.defaultHost);
					}
				}
			}

			try {
				i = 0;
				for (User u: newCache.getUsers()) {//Feed all users as members of their own group (for UserAsGroups indexing mode)
					i++;
					LOG.debug("Processing user " + i + ": " + u.principal);
					Group userAsGroup = new Group(u.principal, u.access, u.namespace, u.caseSensivity);
					userAsGroup.addMember(new User(u.principal, u.access, u.namespace, u.caseSensivity));
					userAsGroup.addMember(new User(u.principal, u.access, "Default", u.caseSensivity));
					try {
						mxos.addMembership(userAsGroup);
					} catch (SendFeed e) {
						LOG.debug("Sending group membership feed to " + gsaInfo.defaultHost);
					}
				}
			} catch (UsersAsGroupsAlreadyResolved doNothing) { }
			
			if (mxos.containsAnyRecord()) {
				SendFeed sf = mxos.closeAndPrepareShipment();
				LOG.debug("Sending group membership feed to " + gsaInfo.defaultHost);
			}
			
		}
	}

	//rewrite all members to be of group type
	private Group membersToGroup(Group g) {
		Group newGroup = new Group(g.principal, g.access, g.namespace, g.caseSensivity);
		for(Principal p : g.getMembers()){
			newGroup.addMember(new Group(p.principal, p.access, p.namespace, p.caseSensivity));
		}
		return newGroup;
	}

	void stop() {
		stop.set(true);
		wakeThread();
	}

	private void wakeThread() {
		synchronized (waitLock) {
			waitLock.notifyAll();
		}
	}

	public void reloadCache() {
		wakeThread();
	}

	private final class CLAwareOIS extends ObjectInputStream {

		private final ClassLoader _cl;

		public CLAwareOIS(final InputStream in, final ClassLoader cl) throws IOException {
			super(in);
			this._cl = cl;
		}

		@Override
		protected Class<?> resolveClass(final ObjectStreamClass desc) throws IOException, ClassNotFoundException {
			try {
				final String name = desc.getName();
				return Class.forName(name, false, _cl);
			} catch (final ClassNotFoundException e) {
				return super.resolveClass(desc);
			}
		}
	}

}
