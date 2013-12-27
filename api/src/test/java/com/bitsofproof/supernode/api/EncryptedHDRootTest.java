package com.bitsofproof.supernode.api;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;

import com.bitsofproof.supernode.common.ByteUtils;
import com.bitsofproof.supernode.common.ExtendedKey;
import com.bitsofproof.supernode.common.ValidationException;
import com.bitsofproof.supernode.wallet.EncryptedHDRoot;
import com.bitsofproof.supernode.wallet.EncryptedHDRoot.ScryptDifficulty;

public class EncryptedHDRootTest
{
	@BeforeClass
	public static void init ()
	{
		Security.addProvider (new BouncyCastleProvider ());
	}

	private static final String TESTS = "EncryptedHDRoot.json";

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

	/**
	 * @throws IOException
	 * @throws JSONException
	 * @throws ValidationException
	 * @throws ParseException
	 */
	@Test
	public void testHDRoot () throws IOException, JSONException, ValidationException, ParseException
	{
		DateFormat dateFormat = new SimpleDateFormat ("dd-MM-yyyy");
		JSONArray testData = readArray (TESTS);
		for ( int i = 0; i < testData.length (); ++i )
		{
			JSONObject test = testData.getJSONObject (i);
			byte[] seed = ByteUtils.fromHex (test.getString ("seed"));
			Date birth = dateFormat.parse (test.getString ("birth"));
			ExtendedKey key = ExtendedKey.create (seed);
			assertTrue (key.getMaster ().getAddress ().toString ().equals (test.getString ("address")));
			assertTrue (key.serialize (true).equals (test.getString ("private")));
			assertTrue (key.getReadOnly ().serialize (true).equals (test.getString ("public")));
			assertTrue (EncryptedHDRoot.encode (seed, birth).equals (test.getString ("clear")));
			assertTrue (EncryptedHDRoot.encrypt (seed, birth, test.getString ("password"), ScryptDifficulty.LOW).equals (test.getString ("encryptedLow")));
			// Travis can not run these as they need lots of memory. Uncomment if curious
			// assertTrue (EncryptedHDRoot.encrypt (seed, birth, test.getString ("password"), ScryptDifficulty.MEDIUM).equals (test.getString
			// ("encryptedMedium")));
			// assertTrue (EncryptedHDRoot.encrypt (seed, birth, test.getString ("password"), ScryptDifficulty.HIGH).equals (test.getString ("encryptedHigh")));
			assertTrue (EncryptedHDRoot.decode (test.getString ("clear")).serialize (true).equals (key.serialize (true)));
			assertTrue (EncryptedHDRoot.decrypt (test.getString ("encryptedLow"), test.getString ("password")).serialize (true).equals (key.serialize
					(true)));
			// Travis can not run these as they need lots of memory. Uncomment if curious
			// assertTrue (EncryptedHDRoot.decrypt (test.getString ("encryptedMedium"), test.getString ("password")).serialize (true)
			// .equals (key.serialize (true)));
			// assertTrue (EncryptedHDRoot.decrypt (test.getString ("encryptedHigh"), test.getString ("password")).serialize (true)
			// .equals (key.serialize (true)));
		}
	}
}
