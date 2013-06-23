package com.bitsofproof.supernode.api;

import com.bitsofproof.supernode.common.Key;

public interface AddressToKeyMap
{
	public Key getKeyForAddress (byte[] address);
}
