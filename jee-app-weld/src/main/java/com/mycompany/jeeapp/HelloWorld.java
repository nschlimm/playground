package com.mycompany.jeeapp;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Model;
import javax.inject.Inject;
import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import org.hibernate.validator.constraints.Email;
import org.hibernate.validator.constraints.NotEmpty;

import com.mycompany.jeeapp.decorator.MyServiceInterface;
import com.mycompany.jeeapp.scope.MyScopeBean;

public @Model
class HelloWorld {

	@SuppressWarnings("unused")
	@Inject
	private MyScopeBean scopeBean;
	
	@Inject
	private MyServiceInterface decoratedInterface;

	private final String text = "Hello World!";

	private String letters;

	private String numbers;

	private String email;

	public HelloWorld() {
	}

	@PostConstruct
	public void initialize() {
		System.out
				.println(this.getClass().getSimpleName() + " was constructed");
		System.out.println("Test");
		decoratedInterface.getDelegate();
	}

	public String getText() {
		return text;
	}

	@NotNull
	@NotEmpty
	@Pattern(regexp = "[A-Za-z]*", message = "must contain only letters")
	public String getLetters() {
		return letters;
	}

	public void setLetters(String letters) {
		this.letters = letters;
	}

	@NotNull
	@NotEmpty
	@Digits(fraction = 0, integer = 2)
	public String getNumbers() {
		return numbers;
	}

	public void setNumbers(String numbers) {
		this.numbers = numbers;
	}

	@NotNull
	@NotEmpty
	@Email
	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

}
