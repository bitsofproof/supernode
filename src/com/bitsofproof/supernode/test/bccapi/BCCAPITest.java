package com.bitsofproof.supernode.test.bccapi;

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;

import com.bccapi.api.APIException;
import com.bitsofproof.supernode.core.ECKeyPair;
import com.bitsofproof.supernode.core.ValidationException;
import com.bitsofproof.supernode.model.BlockStore;
import com.bitsofproof.supernode.plugins.bccapi.ExtendedBitcoinClientAPI;

@RunWith (SpringJUnit4ClassRunner.class)
@ContextConfiguration (inheritLocations = true, value = "/assembly.xml")
public class BCCAPITest
{
	@Autowired
	@Qualifier ("remoteBCCAPI")
	private ExtendedBitcoinClientAPI api;

	@Autowired
	private BlockStore store;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Test
	public void cacheTest ()
	{
		store.cache ();
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
		api.addAddress (sessionId, "17tAKf72nmD6TNdkLsWnj1jhmvFPajMHZw");
		api.getAccountStatement (sessionId, 0, 1000);
		api.getRecentTransactionSummary (sessionId, 10);
	}
}
