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

import com.bitsofproof.supernode.model.Blk;

public abstract class ChainImpl implements Chain
{

	private final long version;
	private final long magic;
	private final int port;
	private final int addressType;
	private final int privateKeyType;
	private final int difficultyReviewBlocks;
	private final int targetBlockTime;
	private final byte[] alertKey;

	public ChainImpl (long version, long magic, int port, int addressType, int privateKeyType, int difficultyReviewBlocks, int targetBlockTime, byte[] alertKey)
	{
		this.version = version;
		this.magic = magic;
		this.port = port;
		this.addressType = addressType;
		this.privateKeyType = privateKeyType;
		this.difficultyReviewBlocks = difficultyReviewBlocks;
		this.targetBlockTime = targetBlockTime;
		this.alertKey = alertKey;
	}

	@Override
	public abstract Blk getGenesis ();

	@Override
	public long getMagic ()
	{
		return magic;
	}

	@Override
	public int getPort ()
	{
		return port;
	}

	@Override
	public int getAddressType ()
	{
		return addressType;
	}

	@Override
	public int getPrivateKeyType ()
	{
		return privateKeyType;
	}

	@Override
	public int getDifficultyReviewBlocks ()
	{
		return difficultyReviewBlocks;
	}

	@Override
	public int getTargetBlockTime ()
	{
		return targetBlockTime;
	}

	@Override
	public byte[] getAlertKey ()
	{
		return alertKey;
	}

	@Override
	public long getVersion ()
	{
		return version;
	}

}
