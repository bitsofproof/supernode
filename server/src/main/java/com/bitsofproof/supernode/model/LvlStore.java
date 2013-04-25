/*
 * Copyright 2013 bits of proof zrt.
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
package com.bitsofproof.supernode.model;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bitsofproof.supernode.api.BloomFilter;
import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.api.WireFormat;
import com.bitsofproof.supernode.core.CachedBlockStore;
import com.bitsofproof.supernode.core.ColorStore;
import com.bitsofproof.supernode.core.Discovery;
import com.bitsofproof.supernode.core.PeerStore;
import com.bitsofproof.supernode.core.TxOutCache;
import com.bitsofproof.supernode.model.OrderedMapStore.DataProcessor;
import com.bitsofproof.supernode.model.OrderedMapStoreKey.KeyType;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

public class LvlStore extends CachedBlockStore implements Discovery, PeerStore, ColorStore
{
	private static final Logger log = LoggerFactory.getLogger (LvlStore.class);

	private OrderedMapStore store;

	public void setStore (OrderedMapStore store)
	{
		this.store = store;
	}

	@Override
	protected void clearStore ()
	{
		store.clearStore ();
		currentHead = null;
	}

	private Tx readTx (String hash) throws ValidationException
	{
		byte[] data = store.get (OrderedMapStoreKey.createKey (KeyType.TX, new Hash (hash).toByteArray ()));
		if ( data != null )
		{
			return Tx.fromLevelDB (data);
		}
		return null;
	}

	private boolean hasTx (String hash)
	{
		return store.get (OrderedMapStoreKey.createKey (KeyType.TX, new Hash (hash).toByteArray ())) != null;
	}

	private void writeTx (Tx t) throws ValidationException
	{
		store.put (OrderedMapStoreKey.createKey (KeyType.TX, new Hash (t.getHash ()).toByteArray ()), t.toLevelDB ());
	}

	private Head readHead (Long id) throws ValidationException
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		writer.writeUint64 (id);
		byte[] data = store.get (OrderedMapStoreKey.createKey (KeyType.HEAD, writer.toByteArray ()));
		if ( data != null )
		{
			return Head.fromLevelDB (data);
		}
		return null;
	}

	private void writeHead (Head h)
	{
		WireFormat.Writer writer = new WireFormat.Writer ();
		writer.writeUint64 (h.getId ());
		store.put (OrderedMapStoreKey.createKey (KeyType.HEAD, writer.toByteArray ()), h.toLevelDB ());
	}

	private Blk readBlk (String hash, boolean full) throws ValidationException
	{
		byte[] data = store.get (OrderedMapStoreKey.createKey (KeyType.BLOCK, new Hash (hash).toByteArray ()));
		if ( data != null )
		{
			Blk b = Blk.fromLevelDB (data);
			if ( full )
			{
				b.setTransactions (new ArrayList<Tx> ());
				for ( String txhash : b.getTxHashes () )
				{
					b.getTransactions ().add (readTx (txhash));
				}
			}
			return b;
		}
		return null;
	}

	private void writeBlk (Blk b) throws ValidationException
	{
		store.put (OrderedMapStoreKey.createKey (KeyType.BLOCK, new Hash (b.getHash ()).toByteArray ()), b.toLevelDB ());
		for ( Tx t : b.getTransactions () )
		{
			// do not overwrite if there since that would reset its available flag
			if ( !hasTx (t.getHash ()) )
			{
				writeTx (t);
			}
		}
	}

	@Override
	protected void updateColor (TxOut root, String fungibleName) throws ValidationException
	{
		Tx t = readTx (root.getTxHash ());
		t.getOutputs ().get (0).setColor (fungibleName);
		store.put (OrderedMapStoreKey.createKey (KeyType.TX, new Hash (t.getHash ()).toByteArray ()), t.toLevelDB ());
	}

	@Override
	public Tx getTransaction (String hash) throws ValidationException
	{
		Tx t = readTx (hash);
		return t;
	}

	private void writePeer (KnownPeer p)
	{
		store.put (OrderedMapStoreKey.createKey (KeyType.PEER, p.getAddress ().getBytes ()), p.toLevelDB ());
	}

	private KnownPeer readPeer (String address) throws ValidationException
	{
		byte[] data = store.get (OrderedMapStoreKey.createKey (KeyType.PEER, address.getBytes ()));
		if ( data != null )
		{
			return KnownPeer.fromLevelDB (data);
		}
		return null;
	}

	@Override
	protected void cacheChain ()
	{
		final List<Blk> blocks = new ArrayList<Blk> ();
		store.forAll (KeyType.BLOCK, new DataProcessor ()
		{
			@Override
			public boolean process (byte[] key, byte[] data)
			{
				Blk b;
				try
				{
					b = Blk.fromLevelDB (data);
					blocks.add (b);
					b.setTxHashes (null); // not needed here save heap
					return true;
				}
				catch ( ValidationException e )
				{
					log.error ("Error parsing block ", e);
					return false;
				}
			}
		});
		Collections.sort (blocks, new Comparator<Blk> ()
		{
			@Override
			public int compare (Blk arg0, Blk arg1)
			{
				return arg0.getHeight () - arg1.getHeight ();
			}
		});
		for ( Blk b : blocks )
		{
			CachedBlock cb = null;
			if ( !b.getPreviousHash ().equals (Hash.ZERO_HASH_STRING) )
			{
				cb =
						new CachedBlock (b.getHash (), b.getId (), cachedBlocks.get (b.getPreviousHash ()), b.getCreateTime (), b.getHeight (),
								(int) b.getVersion (), b.getFilterMap (), b.getFilterFunctions ());
			}
			else
			{
				cb =
						new CachedBlock (b.getHash (), b.getId (), null, b.getCreateTime (), b.getHeight (), (int) b.getVersion (), b.getFilterMap (),
								b.getFilterFunctions ());
			}
			cachedBlocks.put (b.getHash (), cb);
			CachedHead h = cachedHeads.get (b.getHeadId ());
			h.getBlocks ().add (cb);
			h.setLast (cb);
		}
	}

	@Override
	protected void cacheHeads ()
	{
		final Map<CachedHead, Long> prevIds = new HashMap<CachedHead, Long> ();

		store.forAll (KeyType.HEAD, new DataProcessor ()
		{
			@Override
			public boolean process (byte[] key, byte[] data)
			{
				Head h;
				try
				{
					h = Head.fromLevelDB (data);
					CachedHead sh = new CachedHead ();
					sh.setId (h.getId ());
					sh.setChainWork (h.getChainWork ());
					sh.setHeight (h.getHeight ());
					if ( h.getPreviousId () != null )
					{
						prevIds.put (sh, h.getPreviousId ());
						sh.setPreviousHeight (h.getPreviousHeight ());
					}
					cachedHeads.put (h.getId (), sh);
					if ( currentHead == null || currentHead.getChainWork () < sh.getChainWork ()
							|| (currentHead.getChainWork () == sh.getChainWork () && sh.getId () < currentHead.getId ()) )
					{
						currentHead = sh;
					}
					return true;
				}
				catch ( ValidationException e )
				{
					log.error ("Error caching head", e);
					return false;
				}
			}
		});
		for ( CachedHead head : cachedHeads.values () )
		{
			if ( prevIds.containsKey (head) )
			{
				head.setPrevious (cachedHeads.get (prevIds.get (head)));
			}
		}
	}

	@Override
	protected void cacheColors ()
	{
		store.forAll (KeyType.COLOR, new DataProcessor ()
		{
			@Override
			public boolean process (byte[] key, byte[] data)
			{
				WireFormat.Reader reader = new WireFormat.Reader (data);
				StoredColor sc = new StoredColor ();
				sc.setTxHash (reader.readHash ().toString ());
				sc.setTerms (reader.readString ());
				sc.setUnit (reader.readUint64 ());
				sc.setExpiryHeight ((int) reader.readUint32 ());
				sc.setSignature (reader.readVarBytes ());
				sc.setPubkey (reader.readVarBytes ());
				cachedColors.put (sc.getTxHash (), sc);
				return true;
			}
		});
	}

	@Override
	protected void cacheUTXO (final int after, final TxOutCache cache)
	{
		// not implemented for leveldb
	}

	@Override
	protected void backwardCache (Blk b, TxOutCache cache, boolean modify) throws ValidationException
	{
		List<Tx> txs = new ArrayList<Tx> ();
		txs.addAll (b.getTransactions ());
		Collections.reverse (txs);
		for ( Tx t : txs )
		{
			for ( TxOut out : t.getOutputs () )
			{
				if ( modify )
				{
					out.setAvailable (false);
				}
				cache.remove (t.getHash (), out.getIx ());
			}

			for ( TxIn in : t.getInputs () )
			{
				if ( !in.getSourceHash ().equals (Hash.ZERO_HASH_STRING) )
				{
					Tx sourceTx = readTx (in.getSourceHash ());
					TxOut source = sourceTx.getOutputs ().get (in.getIx ().intValue ());
					if ( modify )
					{
						source.setAvailable (true);
						writeTx (sourceTx);
					}

					cache.add (source.flatCopy (null));
				}
			}
			if ( modify )
			{
				writeTx (t);
			}
		}
	}

	@Override
	protected void forwardCache (Blk b, TxOutCache cache, boolean modify) throws ValidationException
	{
		for ( Tx t : b.getTransactions () )
		{
			for ( TxOut out : t.getOutputs () )
			{
				if ( modify )
				{
					out.setAvailable (true);
				}
				cache.add (out.flatCopy (null));
			}

			for ( TxIn in : t.getInputs () )
			{
				if ( !in.getSourceHash ().equals (Hash.ZERO_HASH_STRING) )
				{
					Tx sourceTx = readTx (in.getSourceHash ());
					TxOut source = sourceTx.getOutputs ().get (in.getIx ().intValue ());
					if ( modify )
					{
						source.setAvailable (false);
						writeTx (sourceTx);
					}

					cache.remove (in.getSourceHash (), in.getIx ());
				}
			}
			if ( modify )
			{
				writeTx (t);
			}
		}
	}

	@Override
	protected List<TxOut> findTxOuts (Map<String, HashSet<Long>> need) throws ValidationException
	{
		ArrayList<TxOut> outs = new ArrayList<TxOut> ();

		for ( String hash : need.keySet () )
		{
			Tx t = readTx (hash);
			if ( t != null )
			{
				for ( Long ix : need.get (hash) )
				{
					TxOut out = t.getOutputs ().get (ix.intValue ());
					if ( out.isAvailable () )
					{
						outs.add (out);
					}
				}
			}
		}

		return outs;
	}

	@Override
	protected void checkBIP30Compliance (Set<String> txs, int untilHeight) throws ValidationException
	{
		for ( String hash : txs )
		{
			Tx t = readTx (hash);
			if ( t != null )
			{
				for ( TxOut out : t.getOutputs () )
				{
					if ( out.isAvailable () )
					{
						Blk b = readBlk (t.getBlockHash (), false);
						if ( b.getHeight () <= untilHeight )
						{
							throw new ValidationException ("BIP30 violation block contains unspent tx " + hash);
						}
					}
				}
			}
		}
	}

	@Override
	protected TxOut getSourceReference (TxOut source) throws ValidationException
	{
		return readTx (source.getTxHash ()).getOutputs ().get (source.getIx ().intValue ());
	}

	@Override
	protected void insertBlock (Blk b) throws ValidationException
	{
		writeBlk (b);
	}

	@Override
	protected void insertHead (Head head)
	{
		// unique and ensures order to find the head associated with UTXO in cacheHeads
		long id = System.currentTimeMillis ();
		head.setId (id);
		writeHead (head);
	}

	@Override
	protected Head updateHead (Head head)
	{
		writeHead (head);
		return head;
	}

	@Override
	protected Head retrieveHead (CachedHead cached) throws ValidationException
	{
		return readHead (cached.getId ());
	}

	@Override
	protected Blk retrieveBlock (CachedBlock cached) throws ValidationException
	{
		return readBlk (cached.getHash (), true);
	}

	@Override
	protected Blk retrieveBlockHeader (CachedBlock cached) throws ValidationException
	{
		return readBlk (cached.getHash (), false);
	}

	@Override
	public void scan (final BloomFilter filter, final TransactionProcessor processor)
	{
		store.forAll (KeyType.TX, new DataProcessor ()
		{
			@Override
			public boolean process (byte[] key, byte[] data)
			{
				Tx t;
				try
				{
					t = Tx.fromLevelDB (data);
					if ( t.passesFilter (filter) && isOnTrunk (t.getBlockHash ()) )
					{
						processor.process (t);
					}
				}
				catch ( ValidationException e )
				{
					log.error ("Can not read transaction ", e);
					return false;
				}
				return true;
			}
		});
		processor.process (null);
	}

	@Override
	public Collection<KnownPeer> getConnectablePeers ()
	{
		final List<KnownPeer> peers = new ArrayList<KnownPeer> ();
		store.forAll (KeyType.PEER, new DataProcessor ()
		{
			@Override
			public boolean process (byte[] key, byte[] data)
			{
				KnownPeer p;
				try
				{
					p = KnownPeer.fromLevelDB (data);
					if ( p.getBanned () < System.currentTimeMillis () / 1000 )
					{
						peers.add (p);
					}
				}
				catch ( ValidationException e )
				{
					log.error ("Can not read peer ", e);
					return false;
				}
				return true;
			}
		});
		Collections.sort (peers, new Comparator<KnownPeer> ()
		{

			@Override
			public int compare (KnownPeer o1, KnownPeer o2)
			{
				if ( o1.getPreference () != o2.getPreference () )
				{
					return (int) (o1.getPreference () - o2.getPreference ());
				}
				if ( o1.getResponseTime () != o2.getResponseTime () )
				{
					return (int) (o1.getResponseTime () - o2.getResponseTime ());
				}
				return 0;
			}
		});

		return peers;
	}

	@Override
	public void store (KnownPeer peer)
	{
		writePeer (peer);
	}

	@Override
	public KnownPeer findPeer (InetAddress address) throws ValidationException
	{
		return readPeer (address.getHostName ());
	}

	@Override
	public List<InetAddress> discover ()
	{
		log.trace ("Discovering stored peers");
		List<InetAddress> peers = new ArrayList<InetAddress> ();
		for ( KnownPeer kp : getConnectablePeers () )
		{
			try
			{
				peers.add (InetAddress.getByName (kp.getAddress ()));
			}
			catch ( UnknownHostException e )
			{
			}
		}
		return peers;
	}

	@Override
	public void storeColor (StoredColor color)
	{
		LevelDBStore.COLOR.Builder builder = LevelDBStore.COLOR.newBuilder ();
		builder.setStoreVersion (1);
		builder.setTxHash (ByteString.copyFrom (new Hash (color.getTxHash ()).toByteArray ()));
		builder.setFungibleName (color.getFungibleName ());
		builder.setTerms (color.getTerms ());
		builder.setUnit (color.getUnit ());
		builder.setExpiryHeight (color.getExpiryHeight ());
		builder.setSignature (ByteString.copyFrom (color.getSignature ()));
		builder.setPubkey (ByteString.copyFrom (color.getPubkey ()));
		store.put (OrderedMapStoreKey.createKey (KeyType.COLOR, new Hash (color.getFungibleName ()).toByteArray ()), builder.build ().toByteArray ());
	}

	@Override
	public StoredColor findColor (String hash) throws ValidationException
	{
		byte[] data = store.get (OrderedMapStoreKey.createKey (KeyType.COLOR, new Hash (hash).toByteArray ()));
		if ( data == null )
		{
			return null;
		}
		LevelDBStore.COLOR p;
		try
		{
			p = LevelDBStore.COLOR.parseFrom (data);
			StoredColor sc = new StoredColor ();
			sc.setTxHash (new Hash (p.getTxHash ().toByteArray ()).toString ());
			sc.setFungibleName (p.getFungibleName ());
			sc.setTerms (p.getTerms ());
			sc.setUnit (p.getUnit ());
			sc.setExpiryHeight (p.getExpiryHeight ());
			sc.setSignature (p.getSignature ().toByteArray ());
			sc.setPubkey (p.getPubkey ().toByteArray ());
			return sc;
		}
		catch ( InvalidProtocolBufferException e )
		{
			throw new ValidationException (e);
		}
	}

	@Override
	public boolean isEmpty ()
	{
		return store.isEmpty ();
	}

	@Override
	protected void startBatch ()
	{
		store.startBatch ();
	}

	@Override
	protected void endBatch ()
	{
		store.endBatch ();
	}

	@Override
	protected void cancelBatch ()
	{
		store.cancelBatch ();
	}
}
