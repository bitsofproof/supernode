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
package com.bitsofproof.supernode.test.bccapi;

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;

import com.bccapi.api.APIException;
import com.bccapi.api.BitcoinClientAPI;
import com.bitsofproof.supernode.core.ECKeyPair;
import com.bitsofproof.supernode.core.ValidationException;
import com.bitsofproof.supernode.model.BlockStore;

@RunWith (SpringJUnit4ClassRunner.class)
@ContextConfiguration (inheritLocations = true, value = "/assembly.xml")
public class BCCAPITest
{
	@Autowired
	private BitcoinClientAPI api;

	@Autowired
	private BlockStore store;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Test
	public void cacheTest ()
	{
		store.cache (0);
	}

	@Test
	public void loginTest () throws IOException, APIException, ValidationException
	{
		ECKeyPair keys = ECKeyPair.createNew ();

		byte[] challenge = api.getLoginChallenge (keys.getPublic ());
		String sessionId = api.login (keys.getPublic (), keys.sign (challenge));
		assertTrue (sessionId != null);
	}

	@Test
	public void balanceTest () throws IOException, APIException, ValidationException
	{
		ECKeyPair keys = ECKeyPair.createNew ();

		byte[] challenge = api.getLoginChallenge (keys.getPublic ());
		final String sessionId = api.login (keys.getPublic (), keys.sign (challenge));
		assertTrue (api.getAccountInfo (sessionId).getAvailableBalance () == 0L);
	}

	@Test
	public void accountStatementTest () throws IOException, APIException, ValidationException
	{
		ECKeyPair keys = ECKeyPair.createNew ();

		byte[] challenge = api.getLoginChallenge (keys.getPublic ());
		final String sessionId = api.login (keys.getPublic (), keys.sign (challenge));
		api.getAccountStatement (sessionId, 0, 1000);
		api.getRecentTransactionSummary (sessionId, 10);
	}
}
