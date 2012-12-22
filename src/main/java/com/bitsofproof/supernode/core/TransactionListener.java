package com.bitsofproof.supernode.core;

import com.bitsofproof.supernode.model.Tx;

public interface TransactionListener
{
	public void onTransaction (Tx transaction);
}
