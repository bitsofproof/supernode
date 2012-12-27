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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.ChainParameter;
import com.bitsofproof.supernode.api.Difficulty;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TransactionFactory;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.api.WireFormat;
import com.bitsofproof.supernode.model.Blk;
import com.bitsofproof.supernode.model.Tx;

public class BlockTemplater implements TrunkListener, TransactionListener
{
	private static final Logger log = LoggerFactory.getLogger (BlockTemplater.class);

	private final List<TemplateListener> templateListener = new ArrayList<TemplateListener> ();

	private final Map<String, Transaction> mineable = new HashMap<String, Transaction> ();

	private final Block template = new Block ();

	private String coinbaseAddress;
	private long minimumFee;

	private final ChainParameter chain;
	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool (1);
	private final BitcoinNetwork network;
	private PlatformTransactionManager transactionManager;

	private String previousHash;
	private long nextDifficulty;
	private int nextHeight;

	public void setTransactionManager (PlatformTransactionManager transactionManager)
	{
		this.transactionManager = transactionManager;
	}

	public void setCoinbaseAddress (String coinbaseAddress)
	{
		this.coinbaseAddress = coinbaseAddress;
	}

	public void setMinimumFee (long minimumFee)
	{
		this.minimumFee = minimumFee;
	}

	public BlockTemplater (BitcoinNetwork network, TxHandler txhandler)
	{
		this.network = network;
		BlockStore store = network.getStore ();
		chain = network.getChain ();
		store.addTrunkListener (this);
		txhandler.addTransactionListener (this);
		scheduler.scheduleAtFixedRate (new Runnable ()
		{
			@Override
			public void run ()
			{
				feedWorker ();
			}
		}, 1L, 1L, TimeUnit.SECONDS);
	}

	public void feedWorker ()
	{
		if ( template.getHash () == null )
		{
			return;
		}
		synchronized ( template )
		{
			updateTemplate ();

			for ( TemplateListener listener : templateListener )
			{
				listener.workOn (template);
			}
		}
	}

	private void updateTemplate ()
	{
		template.setCreateTime (System.currentTimeMillis () / 1000);
		template.setDifficultyTarget (nextDifficulty);
		template.setNonce (0);
		template.setPreviousHash (previousHash);
		template.setTransactions (new ArrayList<Transaction> ());
		try
		{
			template.getTransactions ().add (TransactionFactory.createCoinbase (coinbaseAddress, nextHeight, chain));
		}
		catch ( ValidationException e )
		{
			log.error ("Can not create coinbase ", e);
		}
		List<Transaction> feeOrder = new ArrayList<Transaction> ();
		feeOrder.addAll (mineable.values ());
		Collections.sort (feeOrder, new Comparator<Transaction> ()
		{
			@Override
			public int compare (Transaction arg0, Transaction arg1)
			{
				return 0;
			}
		});

		template.computeHash ();
	}

	public void addTemplateListener (TemplateListener listener)
	{
		templateListener.add (listener);
	}

	private void addTransaction (Tx tx)
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		tx.toWire (writer);
		Transaction t = Transaction.fromWire (new WireFormat.Reader (writer.toByteArray ()));
		t.computeHash ();
		mineable.put (t.getHash (), t);
	}

	private void removeTransaction (Tx tx)
	{
		mineable.remove (tx.getHash ());
	}

	@Override
	public void onTransaction (Tx tx)
	{
		synchronized ( template )
		{
			addTransaction (tx);
		}
	}

	@Override
	public void trunkUpdate (final List<String> shortened, final List<String> extended)
	{
		new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult ()
		{
			@Override
			protected void doInTransactionWithoutResult (TransactionStatus status)
			{
				status.setRollbackOnly ();
				synchronized ( template )
				{
					for ( String blockHash : shortened )
					{
						Blk blk = network.getStore ().getBlock (blockHash);
						boolean coinbase = true;
						for ( Tx t : blk.getTransactions () )
						{
							if ( coinbase )
							{
								coinbase = false;
							}
							else
							{
								addTransaction (t);
							}
						}
					}
					for ( final String blockHash : extended )
					{
						Blk blk = network.getStore ().getBlock (blockHash);
						previousHash = blk.getHash ();
						nextHeight = blk.getHeight () + 1;
						if ( nextHeight % chain.getDifficultyReviewBlocks () == 0 )
						{
							nextDifficulty =
									Difficulty.getNextTarget (network.getStore ().getPeriodLength (previousHash, chain.getDifficultyReviewBlocks ()),
											blk.getDifficultyTarget (), chain.getTargetBlockTime ());
						}
						else
						{
							nextDifficulty = blk.getDifficultyTarget ();
						}
						boolean coinbase = true;
						for ( Tx t : blk.getTransactions () )
						{
							if ( coinbase )
							{
								coinbase = false;
							}
							else
							{
								removeTransaction (t);
							}
						}
					}
				}
			}
		});
	}
}
