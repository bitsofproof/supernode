package com.bitsofproof.supernode.plugins.bccapi;

class Client
{
	private final long loginTime;
	private final byte[] pubkey;

	public Client (long loginTime, byte[] pubkey)
	{
		this.loginTime = loginTime;
		this.pubkey = pubkey;
	}

	public long getLoginTime ()
	{
		return loginTime;
	}

}
