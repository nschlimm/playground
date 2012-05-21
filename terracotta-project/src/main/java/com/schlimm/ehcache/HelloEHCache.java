package com.schlimm.ehcache;

import java.io.FileNotFoundException;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

public class HelloEHCache {

	public static void main(String[] args) throws CacheException, FileNotFoundException {
		CacheManager mgr = CacheManager.newInstance();
		System.out.println(mgr.getConfiguration().getCacheConfigurations().toString());
		Cache cache = mgr.getCache("sampleCache1");
		cache.put(new Element("Frank", "Groﬂer Junge!"));
		System.out.println(cache.get("Frank"));
		mgr.shutdown();
	}
}
