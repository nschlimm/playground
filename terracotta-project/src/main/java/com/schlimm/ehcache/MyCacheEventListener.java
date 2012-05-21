package com.schlimm.ehcache;

import java.util.Properties;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Status;
import net.sf.ehcache.event.CacheManagerEventListener;
import net.sf.ehcache.event.CacheManagerEventListenerFactory;

public class MyCacheEventListener extends CacheManagerEventListenerFactory {

	@Override
	public CacheManagerEventListener createCacheManagerEventListener(CacheManager arg0, Properties arg1) {
		return new CacheManagerEventListener() {
			
			@Override
			public void notifyCacheRemoved(String cacheName) {
				System.out.println("Removed: " + cacheName);
			}
			
			@Override
			public void notifyCacheAdded(String cacheName) {
				System.out.println("Added: " + cacheName);				
			}
			
			@Override
			public void init() throws CacheException {
				System.out.println("Initialized");				
			}
			
			@Override
			public Status getStatus() {
				return Status.STATUS_ALIVE;
			}
			
			@Override
			public void dispose() throws CacheException {
				System.out.println("Disposed");				
			}
		};
	}

}
