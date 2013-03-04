package com.bitsofproof.supernode.api;

public class BCSAPIException extends Exception
{
	private static final long serialVersionUID = -816029891683622613L;

	public BCSAPIException ()
	{
		super ();
	}

	public BCSAPIException (String arg0, Throwable arg1)
	{
		super (arg0, arg1);
	}

	public BCSAPIException (String arg0)
	{
		super (arg0);
	}

	public BCSAPIException (Throwable arg0)
	{
		super (arg0);
	}

}
