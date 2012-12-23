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
package com.bitsofproof.supernode.api;

import java.util.List;

/**
 * This is the API extensions to the bitsofproof supernode should build on
 */
public interface BCSAPI extends BCSAPIRemoteCalls
{
	/**
	 * Register a transactions listener
	 * 
	 * @param listener
	 *            will be called for every validated transaction
	 */
	public void registerTransactionListener (TransactionListener listener);

	/**
	 * Register a block listener
	 * 
	 * @param listener
	 *            will be called for every validated new block
	 */
	public void registerTrunkListener (TrunkListener listener);

	/**
	 * Register a block template listener
	 * 
	 * @param listener
	 *            will be called with work suggestions
	 */
	public void registerBlockTemplateListener (TemplateListener listener);

	/**
	 * Register a listener for transactions involving the given addresses
	 * 
	 * @param addresses
	 * @param listener
	 * @return
	 */
	public void registerAccountListener (List<String> addresses, TransactionListener listener);
}
