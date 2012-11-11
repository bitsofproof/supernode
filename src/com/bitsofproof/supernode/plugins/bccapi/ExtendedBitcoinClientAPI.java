package com.bitsofproof.supernode.plugins.bccapi;

import com.bccapi.api.BitcoinClientAPI;

public interface ExtendedBitcoinClientAPI extends BitcoinClientAPI
{
	public void addAddress (String sessionID, String address);
}
