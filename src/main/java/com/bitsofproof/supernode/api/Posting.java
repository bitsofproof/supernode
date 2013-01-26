package com.bitsofproof.supernode.api;

import java.io.Serializable;

import com.google.protobuf.ByteString;

public class Posting implements Serializable
{
	private static final long serialVersionUID = 9116187159627285354L;
	private TransactionOutput output;
	private String block;
	private long timestamp;
	private int height;
	private String spent;

	public TransactionOutput getOutput ()
	{
		return output;
	}

	public void setOutput (TransactionOutput output)
	{
		this.output = output;
	}

	public String getSpent ()
	{
		return spent;
	}

	public void setSpent (String spent)
	{
		this.spent = spent;
	}

	public String getBlock ()
	{
		return block;
	}

	public void setBlock (String block)
	{
		this.block = block;
	}

	public long getTimestamp ()
	{
		return timestamp;
	}

	public void setTimestamp (long timestamp)
	{
		this.timestamp = timestamp;
	}

	public int getHeight ()
	{
		return height;
	}

	public void setHeight (int height)
	{
		this.height = height;
	}

	public BCSAPIMessage.AccountStatement.Posting toProtobuf ()
	{
		BCSAPIMessage.AccountStatement.Posting.Builder builder = BCSAPIMessage.AccountStatement.Posting.newBuilder ();
		builder.setBlock (ByteString.copyFrom (new Hash (block).toByteArray ()));
		builder.setHeight (height);
		builder.setTimestamp ((int) timestamp);
		builder.setOutput (output.toProtobuf ());
		if ( spent != null )
		{
			builder.setSpent (ByteString.copyFrom (new Hash (spent).toByteArray ()));
		}
		return builder.build ();
	}

	public static Posting fromProtobuf (BCSAPIMessage.AccountStatement.Posting pr)
	{
		Posting received = new Posting ();

		received.setBlock (new Hash (pr.getBlock ().toByteArray ()).toString ());
		received.setHeight (pr.getHeight ());
		received.setTimestamp (pr.getTimestamp ());
		received.setOutput (TransactionOutput.fromProtobuf (pr.getOutput ()));
		if ( pr.hasSpent () )
		{
			received.setSpent (new Hash (pr.getSpent ().toByteArray ()).toString ());
		}
		return received;
	}
}
