package com.mycompany.springapp.injection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MyService {
	
	@Autowired
	private SomeDao myDaoAutowiredDirect;
	
	@Value("#{@someDao}")
	private SomeDao myDaoByExpression;
	
	public SomeDao getMyDaoAutowiredDirect() {
		return myDaoAutowiredDirect;
	}

	public SomeDao getMyDaoByExpression() {
		return myDaoByExpression;
	}
	
}
