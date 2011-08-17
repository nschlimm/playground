package com.mycompany.springapp.controller;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.stereotype.Component;

import com.mycompany.springapp.model.Person;
 
@Component
//@Scope("prototype")
public class DataInitializer {

	public static final int PERSON_COUNT = 3;

	@PersistenceContext
	private EntityManager entityManager;

	private List<Long> people = new ArrayList<Long>();

	public void initData() {
		people.clear();// clear out the previous list of people
		addPerson("Jim", "Smith");
		addPerson("Tina", "Marsh");
		addPerson("Steve", "Blair");
		entityManager.flush();
		entityManager.clear();
	}

	public void addPerson(String firstName, String lastName) {
		Person p = new Person();
		p.setFirstName(firstName);
		p.setLastName(lastName);
		entityManager.persist(p);
		people.add(p.getId());
	}
	
	public EntityManager getEntityManager() {
		return entityManager;
	}

	public List<Long> getPeople() {
		return people;
	}

	public void setPeople(List<Long> people) {
		this.people = people;
	}
}
