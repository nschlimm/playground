package com.mycompany.springapp.dao;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import com.mycompany.springapp.model.Person;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PersonDao {

	@PersistenceContext
	private EntityManager entityManager;
	
	public Person find(Long id) {
		return entityManager.find(Person.class, id);
	}
	
	@SuppressWarnings("unchecked")
	public List<Person> getPeople() {
		return entityManager.createQuery("select p from Person p").getResultList();
	}
	
	@Transactional
	public Person save(Person person) {
		if (person.getId() == null) {
			entityManager.persist(person);
			return person;
		} else {
			return entityManager.merge(person);
		}		
	}	
	
}
