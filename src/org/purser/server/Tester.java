package org.purser.server;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Tester {
	@PersistenceContext
	private EntityManager entityManager;

	@Transactional
	public Long testSaveOrderWithItems() throws Exception {
		Order order = new Order();
		order.getItems().add(new Item());
		entityManager.persist(order);
		entityManager.flush();
		return order.getId();
	}

	public static void main (String [] args)
	{
		ApplicationContext context = 
	            new ClassPathXmlApplicationContext("resources/app-context.xml");
		try {
			Tester p = context.getBean(Tester.class);
			p.testSaveOrderWithItems();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
