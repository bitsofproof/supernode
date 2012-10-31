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
package com.bitsofproof.supernode.model;

import java.util.List;

import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.ValidationException;

public interface BlockStore
{

	public void cache ();

	public List<String> getLocator ();

	public void storeBlock (Blk b) throws ValidationException;

	public String getHeadHash ();

	public void resetStore (Chain chain);

	public Blk getBlock (String hash);

	public boolean isStoredBlock (String hash);

	public boolean validateTransaction (Tx tx) throws ValidationException;

	public long getChainHeight ();
}