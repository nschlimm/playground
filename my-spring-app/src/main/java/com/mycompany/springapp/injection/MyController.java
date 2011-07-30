package com.mycompany.springapp.injection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

@Controller
public class MyController {
	
	@Autowired
	private MyService myServiceAutowired;

	public MyService getMyServiceAutowired() {
		return myServiceAutowired;
	}

}
