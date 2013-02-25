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

import java.io.Serializable;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionConsumer;
import javax.jms.ConnectionFactory;
import javax.jms.ConnectionMetaData;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.ServerSessionPool;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

public class InMemoryBusConnectionFactory implements ConnectionFactory
{
	private final MockSession singleSession = new MockSession ();

	private static class MockBytesMessage implements BytesMessage
	{
		Destination destination;
		Destination replyTo;
		private byte[] bytes;

		@Override
		public String getJMSMessageID () throws JMSException
		{
			return null;
		}

		@Override
		public void setJMSMessageID (String id) throws JMSException
		{
		}

		@Override
		public long getJMSTimestamp () throws JMSException
		{
			return 0;
		}

		@Override
		public void setJMSTimestamp (long timestamp) throws JMSException
		{
		}

		@Override
		public byte[] getJMSCorrelationIDAsBytes () throws JMSException
		{
			return null;
		}

		@Override
		public void setJMSCorrelationIDAsBytes (byte[] correlationID) throws JMSException
		{
		}

		@Override
		public void setJMSCorrelationID (String correlationID) throws JMSException
		{
		}

		@Override
		public String getJMSCorrelationID () throws JMSException
		{
			return null;
		}

		@Override
		public Destination getJMSReplyTo () throws JMSException
		{
			return replyTo;
		}

		@Override
		public void setJMSReplyTo (Destination replyTo) throws JMSException
		{
			this.replyTo = replyTo;
		}

		@Override
		public Destination getJMSDestination () throws JMSException
		{
			return destination;
		}

		@Override
		public void setJMSDestination (Destination destination) throws JMSException
		{
			this.destination = destination;
		}

		@Override
		public int getJMSDeliveryMode () throws JMSException
		{
			return 0;
		}

		@Override
		public void setJMSDeliveryMode (int deliveryMode) throws JMSException
		{
		}

		@Override
		public boolean getJMSRedelivered () throws JMSException
		{
			return false;
		}

		@Override
		public void setJMSRedelivered (boolean redelivered) throws JMSException
		{
		}

		@Override
		public String getJMSType () throws JMSException
		{
			return null;
		}

		@Override
		public void setJMSType (String type) throws JMSException
		{
		}

		@Override
		public long getJMSExpiration () throws JMSException
		{
			return 0;
		}

		@Override
		public void setJMSExpiration (long expiration) throws JMSException
		{
		}

		@Override
		public int getJMSPriority () throws JMSException
		{
			return 0;
		}

		@Override
		public void setJMSPriority (int priority) throws JMSException
		{
		}

		@Override
		public void clearProperties () throws JMSException
		{
		}

		@Override
		public boolean propertyExists (String name) throws JMSException
		{
			return false;
		}

		@Override
		public boolean getBooleanProperty (String name) throws JMSException
		{
			return false;
		}

		@Override
		public byte getByteProperty (String name) throws JMSException
		{
			return 0;
		}

		@Override
		public short getShortProperty (String name) throws JMSException
		{
			return 0;
		}

		@Override
		public int getIntProperty (String name) throws JMSException
		{
			return 0;
		}

		@Override
		public long getLongProperty (String name) throws JMSException
		{
			return 0;
		}

		@Override
		public float getFloatProperty (String name) throws JMSException
		{
			return 0;
		}

		@Override
		public double getDoubleProperty (String name) throws JMSException
		{
			return 0;
		}

		@Override
		public String getStringProperty (String name) throws JMSException
		{
			return null;
		}

		@Override
		public Object getObjectProperty (String name) throws JMSException
		{
			return null;
		}

		@SuppressWarnings ("rawtypes")
		@Override
		public Enumeration getPropertyNames () throws JMSException
		{
			return null;
		}

		@Override
		public void setBooleanProperty (String name, boolean value) throws JMSException
		{
		}

		@Override
		public void setByteProperty (String name, byte value) throws JMSException
		{
		}

		@Override
		public void setShortProperty (String name, short value) throws JMSException
		{
		}

		@Override
		public void setIntProperty (String name, int value) throws JMSException
		{
		}

		@Override
		public void setLongProperty (String name, long value) throws JMSException
		{
		}

		@Override
		public void setFloatProperty (String name, float value) throws JMSException
		{
		}

		@Override
		public void setDoubleProperty (String name, double value) throws JMSException
		{
		}

		@Override
		public void setStringProperty (String name, String value) throws JMSException
		{
		}

		@Override
		public void setObjectProperty (String name, Object value) throws JMSException
		{
		}

		@Override
		public void acknowledge () throws JMSException
		{
		}

		@Override
		public void clearBody () throws JMSException
		{
		}

		@Override
		public long getBodyLength () throws JMSException
		{
			return bytes.length;
		}

		@Override
		public boolean readBoolean () throws JMSException
		{
			return false;
		}

		@Override
		public byte readByte () throws JMSException
		{
			return 0;
		}

		@Override
		public int readUnsignedByte () throws JMSException
		{
			return 0;
		}

		@Override
		public short readShort () throws JMSException
		{
			return 0;
		}

		@Override
		public int readUnsignedShort () throws JMSException
		{
			return 0;
		}

		@Override
		public char readChar () throws JMSException
		{
			return 0;
		}

		@Override
		public int readInt () throws JMSException
		{
			return 0;
		}

		@Override
		public long readLong () throws JMSException
		{
			return 0;
		}

		@Override
		public float readFloat () throws JMSException
		{
			return 0;
		}

		@Override
		public double readDouble () throws JMSException
		{
			return 0;
		}

		@Override
		public String readUTF () throws JMSException
		{
			return null;
		}

		@Override
		public int readBytes (byte[] value) throws JMSException
		{
			return readBytes (value, value.length);
		}

		@Override
		public int readBytes (byte[] value, int length) throws JMSException
		{
			int l = Math.min (length, bytes.length);
			System.arraycopy (bytes, 0, value, 0, l);
			return l;
		}

		@Override
		public void writeBoolean (boolean value) throws JMSException
		{
		}

		@Override
		public void writeByte (byte value) throws JMSException
		{
		}

		@Override
		public void writeShort (short value) throws JMSException
		{
		}

		@Override
		public void writeChar (char value) throws JMSException
		{
		}

		@Override
		public void writeInt (int value) throws JMSException
		{
		}

		@Override
		public void writeLong (long value) throws JMSException
		{
		}

		@Override
		public void writeFloat (float value) throws JMSException
		{
		}

		@Override
		public void writeDouble (double value) throws JMSException
		{
		}

		@Override
		public void writeUTF (String value) throws JMSException
		{
		}

		@Override
		public void writeBytes (byte[] value) throws JMSException
		{
			writeBytes (value, 0, value.length);
		}

		@Override
		public void writeBytes (byte[] value, int offset, int length) throws JMSException
		{
			bytes = new byte[length];
			System.arraycopy (value, offset, bytes, 0, length);
		}

		@Override
		public void writeObject (Object value) throws JMSException
		{
		}

		@Override
		public void reset () throws JMSException
		{
		}

	}

	private static class MockTopic implements Topic
	{
		private final String name;

		public MockTopic (String name)
		{
			this.name = name;
		}

		@Override
		public String getTopicName () throws JMSException
		{
			return name;
		}
	}

	private static class MockQueue implements Queue
	{
		private final String name;

		public MockQueue (String name)
		{
			this.name = name;
		}

		@Override
		public String getQueueName () throws JMSException
		{
			return name;
		}
	}

	private static class MockTemporaryQueue implements TemporaryQueue
	{
		private final String name;

		public MockTemporaryQueue (String name)
		{
			this.name = name;
		}

		@Override
		public String getQueueName () throws JMSException
		{
			return name;
		}

		@Override
		public void delete () throws JMSException
		{
		}
	}

	private static class MockProducer implements MessageProducer
	{
		private final LinkedBlockingQueue<Message> queue;

		public MockProducer (LinkedBlockingQueue<Message> queue)
		{
			this.queue = queue;
		}

		@Override
		public void setDisableMessageID (boolean value) throws JMSException
		{
		}

		@Override
		public boolean getDisableMessageID () throws JMSException
		{
			return false;
		}

		@Override
		public void setDisableMessageTimestamp (boolean value) throws JMSException
		{
		}

		@Override
		public boolean getDisableMessageTimestamp () throws JMSException
		{
			return false;
		}

		@Override
		public void setDeliveryMode (int deliveryMode) throws JMSException
		{
		}

		@Override
		public int getDeliveryMode () throws JMSException
		{
			return 0;
		}

		@Override
		public void setPriority (int defaultPriority) throws JMSException
		{
		}

		@Override
		public int getPriority () throws JMSException
		{
			return 0;
		}

		@Override
		public void setTimeToLive (long timeToLive) throws JMSException
		{
		}

		@Override
		public long getTimeToLive () throws JMSException
		{
			return 0;
		}

		@Override
		public Destination getDestination () throws JMSException
		{
			return null;
		}

		@Override
		public void close () throws JMSException
		{
		}

		@Override
		public void send (Message message) throws JMSException
		{
			queue.add (message);
		}

		@Override
		public void send (Message message, int deliveryMode, int priority, long timeToLive) throws JMSException
		{
			queue.add (message);
		}

		@Override
		public void send (Destination destination, Message message) throws JMSException
		{
			queue.add (message);
		}

		@Override
		public void send (Destination destination, Message message, int deliveryMode, int priority, long timeToLive) throws JMSException
		{
			queue.add (message);
		}
	}

	private static class MockConsumer implements MessageConsumer
	{
		private final LinkedBlockingQueue<Message> queue;
		private MessageListener listener = null;

		public MockConsumer (LinkedBlockingQueue<Message> queue)
		{
			this.queue = queue;
			Thread lt = new Thread (new Runnable ()
			{
				@Override
				public void run ()
				{
					Message m = null;
					try
					{
						while ( (m = receive ()) != null )
						{
							if ( listener != null )
							{
								listener.onMessage (m);
							}
						}
					}
					catch ( JMSException e )
					{
					}
				}
			});
			lt.setDaemon (true);
			lt.start ();
		}

		@Override
		public String getMessageSelector () throws JMSException
		{
			return null;
		}

		@Override
		public MessageListener getMessageListener () throws JMSException
		{
			return null;
		}

		@Override
		public void setMessageListener (MessageListener listener) throws JMSException
		{
			this.listener = listener;
		}

		@Override
		public Message receive () throws JMSException
		{
			try
			{
				return queue.take ();
			}
			catch ( InterruptedException e )
			{
				return null;
			}
		}

		@Override
		public Message receive (long timeout) throws JMSException
		{
			try
			{
				return queue.poll (timeout, TimeUnit.MILLISECONDS);
			}
			catch ( InterruptedException e )
			{
				return null;
			}
		}

		@Override
		public Message receiveNoWait () throws JMSException
		{
			return queue.poll ();
		}

		@Override
		public void close () throws JMSException
		{
		}
	}

	private static class MockSession implements Session
	{
		Map<String, LinkedBlockingQueue<Message>> queue = new HashMap<String, LinkedBlockingQueue<Message>> ();

		@Override
		public BytesMessage createBytesMessage () throws JMSException
		{
			return new MockBytesMessage ();
		}

		@Override
		public MapMessage createMapMessage () throws JMSException
		{
			return null;
		}

		@Override
		public Message createMessage () throws JMSException
		{
			return null;
		}

		@Override
		public ObjectMessage createObjectMessage () throws JMSException
		{
			return null;
		}

		@Override
		public ObjectMessage createObjectMessage (Serializable object) throws JMSException
		{
			return null;
		}

		@Override
		public StreamMessage createStreamMessage () throws JMSException
		{
			return null;
		}

		@Override
		public TextMessage createTextMessage () throws JMSException
		{
			return null;
		}

		@Override
		public TextMessage createTextMessage (String text) throws JMSException
		{
			return null;
		}

		@Override
		public boolean getTransacted () throws JMSException
		{
			return false;
		}

		@Override
		public int getAcknowledgeMode () throws JMSException
		{
			return 0;
		}

		@Override
		public void commit () throws JMSException
		{
		}

		@Override
		public void rollback () throws JMSException
		{
		}

		@Override
		public void close () throws JMSException
		{
		}

		@Override
		public void recover () throws JMSException
		{
		}

		@Override
		public MessageListener getMessageListener () throws JMSException
		{
			return null;
		}

		@Override
		public void setMessageListener (MessageListener listener) throws JMSException
		{
		}

		@Override
		public void run ()
		{
		}

		@Override
		public MessageProducer createProducer (Destination destination) throws JMSException
		{
			String name = null;
			if ( destination instanceof MockTopic )
			{
				name = ((MockTopic) destination).getTopicName ();
			}
			if ( destination instanceof MockQueue )
			{
				name = ((MockQueue) destination).getQueueName ();
			}
			if ( destination instanceof MockTemporaryQueue )
			{
				name = ((MockTemporaryQueue) destination).getQueueName ();
			}

			LinkedBlockingQueue<Message> list = queue.get (name);
			if ( list == null )
			{
				list = new LinkedBlockingQueue<Message> ();
				queue.put (name, list);
			}
			return new MockProducer (list);
		}

		@Override
		public MessageConsumer createConsumer (Destination destination) throws JMSException
		{
			String name = null;
			if ( destination instanceof MockTopic )
			{
				name = ((MockTopic) destination).getTopicName ();
			}
			if ( destination instanceof MockQueue )
			{
				name = ((MockQueue) destination).getQueueName ();
			}
			if ( destination instanceof MockTemporaryQueue )
			{
				name = ((MockTemporaryQueue) destination).getQueueName ();
			}
			LinkedBlockingQueue<Message> list = queue.get (name);
			if ( list == null )
			{
				list = new LinkedBlockingQueue<Message> ();
				queue.put (name, list);
			}
			return new MockConsumer (list);
		}

		@Override
		public MessageConsumer createConsumer (Destination destination, String messageSelector) throws JMSException
		{
			return null;
		}

		@Override
		public MessageConsumer createConsumer (Destination destination, String messageSelector, boolean NoLocal) throws JMSException
		{
			return null;
		}

		@Override
		public Queue createQueue (final String queueName) throws JMSException
		{
			return new MockQueue (queueName);
		}

		@Override
		public Topic createTopic (final String topicName) throws JMSException
		{
			return new MockTopic (topicName);
		}

		@Override
		public TopicSubscriber createDurableSubscriber (Topic topic, String name) throws JMSException
		{
			return null;
		}

		@Override
		public TopicSubscriber createDurableSubscriber (Topic topic, String name, String messageSelector, boolean noLocal) throws JMSException
		{
			return null;
		}

		@Override
		public QueueBrowser createBrowser (Queue queue) throws JMSException
		{
			return null;
		}

		@Override
		public QueueBrowser createBrowser (Queue queue, String messageSelector) throws JMSException
		{
			return null;
		}

		@Override
		public TemporaryQueue createTemporaryQueue () throws JMSException
		{
			return new MockTemporaryQueue (this.toString ());
		}

		@Override
		public TemporaryTopic createTemporaryTopic () throws JMSException
		{
			return null;
		}

		@Override
		public void unsubscribe (String name) throws JMSException
		{
		}
	}

	private class MockConnection implements Connection
	{
		@Override
		public Session createSession (boolean transacted, int acknowledgeMode) throws JMSException
		{
			return singleSession;
		}

		@Override
		public String getClientID () throws JMSException
		{
			return null;
		}

		@Override
		public void setClientID (String clientID) throws JMSException
		{
		}

		@Override
		public ConnectionMetaData getMetaData () throws JMSException
		{
			return null;
		}

		@Override
		public ExceptionListener getExceptionListener () throws JMSException
		{
			return null;
		}

		@Override
		public void setExceptionListener (ExceptionListener listener) throws JMSException
		{
		}

		@Override
		public void start () throws JMSException
		{
		}

		@Override
		public void stop () throws JMSException
		{
		}

		@Override
		public void close () throws JMSException
		{
		}

		@Override
		public ConnectionConsumer createConnectionConsumer (Destination destination, String messageSelector, ServerSessionPool sessionPool, int maxMessages)
				throws JMSException
		{
			return null;
		}

		@Override
		public ConnectionConsumer createDurableConnectionConsumer (Topic topic, String subscriptionName, String messageSelector, ServerSessionPool sessionPool,
				int maxMessages) throws JMSException
		{
			return null;
		}

	}

	@Override
	public Connection createConnection () throws JMSException
	{
		return new MockConnection ();
	}

	@Override
	public Connection createConnection (String userName, String password) throws JMSException
	{
		return new MockConnection ();
	}
}
