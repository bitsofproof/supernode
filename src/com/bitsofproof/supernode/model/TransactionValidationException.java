package com.bitsofproof.supernode.model;

import com.bitsofproof.supernode.core.ValidationException;

public class TransactionValidationException extends ValidationException
{
	private static final long serialVersionUID = 1L;
	private final Tx tx;
	private final int in;

	public TransactionValidationException (String message, Tx tx, int in)
	{
		super (message);
		this.tx = tx;
		this.in = in;
	}

	public TransactionValidationException (Exception e, Tx tx)
	{
		super (e);
		this.tx = tx;
		this.in = -1;
	}

	public TransactionValidationException (Exception e, Tx tx, int in)
	{
		super (e);
		this.tx = tx;
		this.in = in;
	}

	public TransactionValidationException (String message, Tx tx)
	{
		super (message);
		this.tx = tx;
		this.in = -1;
	}

	public Tx getTx ()
	{
		return tx;
	}

	public int getIn ()
	{
		return in;
	}
}
