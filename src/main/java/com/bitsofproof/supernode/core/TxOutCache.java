package com.bitsofproof.supernode.core;

import com.bitsofproof.supernode.model.TxOut;

public interface TxOutCache
{

	public TxOut get (String hash, Long ix);

	public void copy (TxOutCache other, String hash);

	public void add (TxOut out);

	public void remove (String hash, Long ix);

}