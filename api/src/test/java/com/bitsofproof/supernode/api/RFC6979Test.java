package com.bitsofproof.supernode.api;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.util.Arrays;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import com.bitsofproof.supernode.common.ByteUtils;
import com.bitsofproof.supernode.common.ECKeyPair;
import com.bitsofproof.supernode.common.ValidationException;

public class RFC6979Test
{
	static BouncyCastleProvider provider;

	@BeforeClass
	public static void init ()
	{
		Security.addProvider (provider = new BouncyCastleProvider ());
	}

	private static final String TESTS = "RFC6979.json";

	private JSONArray readArray (String resource) throws IOException, JSONException
	{
		InputStream input = this.getClass ().getResource ("/" + resource).openStream ();
		StringBuffer content = new StringBuffer ();
		byte[] buffer = new byte[1024];
		int len;
		while ( (len = input.read (buffer)) > 0 )
		{
			byte[] s = new byte[len];
			System.arraycopy (buffer, 0, s, 0, len);
			content.append (new String (buffer, "UTF-8"));
		}
		return new JSONArray (content.toString ());
	}

	@Test
	public void rfc6979 () throws IOException, JSONException, ValidationException
	{
		JSONArray tests = readArray (TESTS);
		for ( int i = 0; i < tests.length (); ++i )
		{
			JSONObject test = tests.getJSONObject (i);
			ECKeyPair key = ECKeyPair.parseWIF (test.getString ("key"));
			byte[] message = test.getString ("message").getBytes ();
			byte[] expectedSignature = ByteUtils.fromHex (test.getString ("expectedSignature"));
			byte[] signature = key.sign (message);
			assertTrue (Arrays.equals (expectedSignature, signature));
		}
	}
}
