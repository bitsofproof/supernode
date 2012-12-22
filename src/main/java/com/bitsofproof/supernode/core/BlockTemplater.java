/*
 * Copyright 2012 Tamas Blummer tamas@bitsofproof.com
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
package com.bitsofproof.supernode.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;

public class BlockTemplater implements ChainListener, TransactionListener
{
	private final List<TemplateListener> templateListener = new ArrayList<TemplateListener> ();
	private final Blk template = new Blk ();
	private final List<Tx> validTransactions = Collections.synchronizedList (new ArrayList<Tx> ());

	private String coinbaseAddress;

	public void setCoinbaseAddress (String coinbaseAddress)
	{
		this.coinbaseAddress = coinbaseAddress;
	}

	public BlockTemplater (ChainLoader loader, TxHandler txhandler)
	{
		loader.addChainListener (this);
		txhandler.addTransactionListener (this);
		initTemplate ();
	}

	public void addTemplateListener (TemplateListener listener)
	{
		templateListener.add (listener);
	}

	private synchronized void initTemplate ()
	{
		// TODO:missing implementation
	}

	@Override
	public void blockAdded (Blk blk)
	{
		initTemplate ();
		// TODO:missing implementation
	}

	@Override
	public void onTransaction (Tx transaction)
	{
		validTransactions.add (transaction);
	}
}
