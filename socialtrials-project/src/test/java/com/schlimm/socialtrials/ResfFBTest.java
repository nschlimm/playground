package com.schlimm.socialtrials;

import org.junit.Test;

import com.restfb.Connection;
import com.restfb.DefaultFacebookClient;
import com.restfb.FacebookClient;
import com.restfb.types.Post;
import com.restfb.types.User;

public class ResfFBTest {

	@Test
	public void test() {
        System.setProperty("https.proxyHost", "provproxy01.provinzial.com");
        System.setProperty("https.proxyPort", "80");
        System.setProperty("https.proxyUser", "pd89809");
        System.setProperty("https.proxyPassword", "abcdef");

		FacebookClient client = new DefaultFacebookClient("AAACEdEose0cBAAZBH5imQyR1q8SFQkfust01DuKqbuVHxjCfHscuDErGEHT2BvcxVtRDbxup1NCk6iNG7irWEaEKNIwkXK8v4iZBZBoC3K9UX3uN1jo");
        Connection<Post> result = client.fetchConnection("100001227223660/home", Post.class);
	}
}
