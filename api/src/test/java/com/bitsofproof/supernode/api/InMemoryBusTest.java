/*
 * Copyright 2013 Tamas Blummer tamas@bitsofproof.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bitsofproof.supernode.api;

import static org.junit.Assert.assertTrue;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.junit.Test;

public class InMemoryBusTest
{
	@Test
	public void mockTopicTest () throws JMSException
	{
		ConnectionFactory factory = new InMemoryBusConnectionFactory ();
		Connection connection = factory.createConnection ();
		Session session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
		MessageProducer producer = session.createProducer (session.createTopic ("test"));
		final MessageConsumer consumer = session.createConsumer (session.createTopic ("test"));
		consumer.setMessageListener (new MessageListener ()
		{
			@Override
			public void onMessage (Message message)
			{
				assertTrue (message instanceof BytesMessage);
				BytesMessage m = (BytesMessage) message;

				byte[] body;
				try
				{
					body = new byte[(int) m.getBodyLength ()];
					m.readBytes (body);
					assertTrue (new String (body).equals ("hello"));
				}
				catch ( JMSException e )
				{
					assertTrue (false);
				}
				synchronized ( consumer )
				{
					consumer.notify ();
				}
			}
		});

		BytesMessage m = session.createBytesMessage ();
		m.writeBytes ("hello".getBytes ());
		producer.send (m);
		try
		{
			synchronized ( consumer )
			{
				consumer.wait ();
			}
		}
		catch ( InterruptedException e )
		{
		}
	}

	@Test
	public void mockQueueTest () throws JMSException
	{
		ConnectionFactory factory = new InMemoryBusConnectionFactory ();
		Connection connection = factory.createConnection ();
		final Session session = connection.createSession (false, Session.AUTO_ACKNOWLEDGE);
		MessageProducer producer = session.createProducer (session.createTopic ("test"));
		final MessageConsumer consumer = session.createConsumer (session.createTopic ("test"));
		consumer.setMessageListener (new MessageListener ()
		{
			@Override
			public void onMessage (Message message)
			{
				assertTrue (message instanceof BytesMessage);
				BytesMessage m = (BytesMessage) message;

				byte[] body;
				try
				{
					body = new byte[(int) m.getBodyLength ()];
					m.readBytes (body);
					assertTrue (new String (body).equals ("hello"));

					MessageProducer replyProducer = session.createProducer (m.getJMSReplyTo ());
					replyProducer.send (m);
				}
				catch ( JMSException e )
				{
					assertTrue (false);
				}
			}
		});

		Destination temp = session.createTemporaryQueue ();
		final MessageConsumer replyConsumer = session.createConsumer (temp);
		replyConsumer.setMessageListener (new MessageListener ()
		{
			@Override
			public void onMessage (Message message)
			{
				assertTrue (message instanceof BytesMessage);
				BytesMessage m = (BytesMessage) message;

				byte[] body;
				try
				{
					body = new byte[(int) m.getBodyLength ()];
					m.readBytes (body);
					assertTrue (new String (body).equals ("hello"));
				}
				catch ( JMSException e )
				{
					assertTrue (false);
				}
				synchronized ( replyConsumer )
				{
					replyConsumer.notify ();
				}
			}
		});

		BytesMessage m = session.createBytesMessage ();
		m.writeBytes ("hello".getBytes ());
		m.setJMSReplyTo (temp);
		producer.send (m);

		try
		{
			synchronized ( replyConsumer )
			{
				replyConsumer.wait ();
			}
		}
		catch ( InterruptedException e )
		{
		}
	}
}
