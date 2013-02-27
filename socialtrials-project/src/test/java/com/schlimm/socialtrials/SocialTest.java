package com.schlimm.socialtrials;

import org.junit.Test;
import org.springframework.social.connect.Connection;
import org.springframework.social.facebook.api.Facebook;
import org.springframework.social.facebook.api.impl.FacebookTemplate;
import org.springframework.social.facebook.connect.FacebookConnectionFactory;
import org.springframework.social.oauth2.AccessGrant;

public class SocialTest {

	@Test
	public void socialConnection() {
//		FacebookConnectionFactory connectionFactory = 
//			    new FacebookConnectionFactory("549411951746481", "526a45b2c946cfa242ee84f8a3ab4910");
//		connectionFactory.getOAuthOperations();
//		AccessGrant grant = new AccessGrant("549411951746481|COnFP__5_39HMKTYfJ7xeMKWaKk");
//		Connection<Facebook> connection = connectionFactory.createConnection(grant);
//		Facebook fb = connection.getApi();
//		System.out.println(fb.feedOperations().getFeed());
        System.setProperty("https.proxyHost", "provproxy01.provinzial.com");
        System.setProperty("https.proxyPort", "80");
        System.setProperty("https.proxyUser", "pd05417");
        System.setProperty("https.proxyPassword", "Iwg9maH!");
		FacebookTemplate template = new FacebookTemplate("AAAHzrZCA4HbEBANr2yp7GXsg9xia0IZCg7Lprq3ZCSXKzB8HPNLCqsCZCIuGp8NQBR5Xj6B1PtT4t7srfW6LlmgDdJMO9g4B89ZCQYnGorw3ZAc2qhQKOm");
		System.out.println(template.feedOperations().getPosts().get(0).getMessage());
	}
}
