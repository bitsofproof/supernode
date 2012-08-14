package org.purser.server;

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

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
	
	static String getPassword () throws IOException
	{
		  String password = null;
		  File master = new File ("MASTERPASSWORD");
		  if ( master.exists() )
		  {
			  FileReader reader = new FileReader(master);
			  password = new BufferedReader(reader).readLine();
			  reader.close();
		  }
		  if ( password == null )
		  {
			  Console console = System.console();
			    if (console == null) {
			      throw new IOException ("unable to obtain console");
			    }

			    password = new String(console.readPassword("MASTERPASSWORD: "));
		  }
		  return password;
	}

	public static void main (String [] args) throws IOException
	{

		String password = Tester.getPassword ();
		  System.setProperty("javax.net.ssl.keyStorePassword", password);
		  System.setProperty("javax.net.ssl.trustStorePassword", password);

		ApplicationContext context = 
	            new ClassPathXmlApplicationContext("app-context.xml");
		try {
			Tester p = context.getBean(Tester.class);
			p.testSaveOrderWithItems();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
