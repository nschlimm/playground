package com.mycompany.jeeapp.scope;

import java.io.Serializable;

import javax.enterprise.context.Conversation;
import javax.enterprise.context.ConversationScoped;
import javax.inject.Inject;

@ConversationScoped
public class MyConversationService implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 469994668783893108L;

	@Inject
	private Conversation conversation;

	private String someName = "myName";

	public void setSomeName(String someName) {
		this.someName = someName;
	}

	public String getSomeName() {
		return someName;
	}

	public void setConversation(Conversation conversation) {
		this.conversation = conversation;
	}

	public Conversation getConversation() {
		return conversation;
	}
	
}
