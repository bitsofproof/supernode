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
package com.bitsofproof.supernode.test.unit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import com.bitsofproof.supernode.core.ByteUtils;
import com.bitsofproof.supernode.core.Hash;
import com.bitsofproof.supernode.core.Script;
import com.bitsofproof.supernode.core.ValidationException;
import com.bitsofproof.supernode.core.WireFormat;
import com.bitsofproof.supernode.model.Tx;

public class ScriptTest
{
	@BeforeClass
	public static void setup ()
	{
	}

	@Test
	public void testGenesis () throws ValidationException
	{
		assertTrue (Script
				.parse (ByteUtils
						.fromHex ("4104678afdb0fe5548271967f1a67130b7105cd6a828e03909a67962e0ea1f61deb649f6bc3f4cef38c4f35504e51ec112de5c384df7ba0b8d578a4c702b6bf11d5fac"))
				.get (1).op == Script.Opcode.OP_CHECKSIG);
	}

	@Test
	public void stringTest ()
	{
		assertTrue (Script.toReadable (Script.fromReadable ("OP_PUSH2 abcd")).equals ("abcd"));

		assertTrue (Script
				.toReadable (
						Script.fromReadable ("OP_PUSH71 3044022002dbe4b5a2fbb521e4dc5fbec75fd960651a2754b03d0871b8c965469be50fa702206d97421fb7ea9359b63e48c2108223284b9a71560bd8182469b9039228d7b3d701 OP_PUSH33 0295bf727111acdeab8778284f02b768d1e21acbcbae42090cc49aaa3cc6d19cda"))
				.equals (
						"3044022002dbe4b5a2fbb521e4dc5fbec75fd960651a2754b03d0871b8c965469be50fa702206d97421fb7ea9359b63e48c2108223284b9a71560bd8182469b9039228d7b3d701 0295bf727111acdeab8778284f02b768d1e21acbcbae42090cc49aaa3cc6d19cda"));
	}

	@Test
	public void dataPushTest ()
	{
		assertTrue (new Script ().evaluateSingleScript (Script.fromReadable ("OP_PUSH3 0a0b0c OP_PUSHDATA1 03 0a0b0c OP_EQUAL")));
	}

	@Test
	public void ifTest ()
	{
		assertTrue (new Script ().evaluateSingleScript (Script.fromReadable ("OP_1 OP_1 OP_EQUAL")));
		assertTrue (new Script ().evaluateSingleScript (Script.fromReadable ("OP_1 OP_IF OP_1 OP_ELSE OP_1 OP_ENDIF")));
		assertFalse (new Script ().evaluateSingleScript (Script.fromReadable ("OP_1 OP_IF OP_FALSE OP_ELSE OP_1 OP_ENDIF OP_EQUAL")));
		assertFalse (new Script ().evaluateSingleScript (Script.fromReadable ("OP_1 OP_IF OP_1 OP_IF OP_FALSE OP_ENDIF OP_ELSE OP_1 OP_ENDIF OP_EQUAL")));
		assertTrue (new Script ().evaluateSingleScript (Script
				.fromReadable ("OP_1 OP_NOTIF OP_FALSE OP_IF OP_FALSE OP_ENDIF OP_ELSE OP_1 OP_ENDIF OP_1 OP_EQUAL")));
	}

	@Test
	public void stackTest ()
	{
		assertTrue (new Script ().evaluateSingleScript (Script.fromReadable ("OP_1 OP_TOALTSTACK OP_FALSE OP_FROMALTSTACK OP_1 OP_EQUAL")));
		assertTrue (new Script ().evaluateSingleScript (Script.fromReadable ("OP_1 OP_2 OP_SWAP OP_1 OP_EQUAL")));
		assertTrue (new Script ().evaluateSingleScript (Script.fromReadable ("OP_1 OP_2 OP_3 OP_1 OP_PICK OP_2 OP_EQUAL")));
	}

	@Test
	public void mathTest ()
	{
		assertTrue (new Script ().evaluateSingleScript (Script.fromReadable ("OP_1 OP_2 OP_ADD OP_3 OP_EQUAL")));
		assertTrue (new Script ().evaluateSingleScript (Script.fromReadable ("OP_3 OP_DUP OP_SUB OP_FALSE OP_EQUAL")));
		assertTrue (new Script ().evaluateSingleScript (Script.fromReadable ("OP_1 OP_5 OP_SUB OP_ABS OP_4 OP_EQUAL")));
		assertTrue (new Script ().evaluateSingleScript (Script.fromReadable ("OP_1 OP_5 OP_MAX OP_2 OP_MIN OP_2 OP_EQUAL")));
	}

	@Test
	public void digestTest ()
	{
		byte[] b = { 'H', 'e', 'l', 'l', 'o', ' ', 'w', 'o', 'r', 'l', 'd', '!' };
		byte[] h = Hash.sha256 (b);

		assertTrue (new Script ().evaluateSingleScript (Script.fromReadable ("OP_PUSHDATA1 0" + Integer.toString (b.length, 16) + " " + ByteUtils.toHex (b)
				+ " OP_SHA256 OP_PUSHDATA1 20 " + ByteUtils.toHex (h) + " OP_EQUAL")));
	}

	@Test
	public void transactionTest () throws ValidationException
	{
		WireFormat.Reader reader =
				new WireFormat.Reader (
						ByteUtils
								.fromHex ("0100000001169e1e83e930853391bc6f35f605c6754cfead57cf8387639d3b4096c54f18f40100000048473044022027542a94d6646c51240f23a76d33088d3dd8815b25e9ea18cac67d1171a3212e02203baf203c6e7b80ebd3e588628466ea28be572fe1aaa3f30947da4763dd3b3d2b01ffffffff0200ca9a3b00000000434104b5abd412d4341b45056d3e376cd446eca43fa871b51961330deebd84423e740daa520690e1d9e074654c59ff87b408db903649623e86f1ca5412786f61ade2bfac005ed0b20000000043410411db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5cb2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3ac00000000"));
		Tx t1 = new Tx ();
		t1.fromWire (reader);
		assertTrue (t1.getHash ().equals ("a16f3ce4dd5deb92d98ef5cf8afeaf0775ebca408f708b2146c4fb42b41e14be"));

		reader =
				new WireFormat.Reader (
						ByteUtils
								.fromHex ("0100000001c997a5e56e104102fa209c6a852dd90660a20b2d9c352423edce25857fcd3704000000004847304402204e45e16932b8af514961a1d3a1a25fdf3f4f7732e9d624c6c61548ab5fb8cd410220181522ec8eca07de4860a4acdd12909d831cc56cbbac4622082221a8768d1d0901ffffffff0200ca9a3b00000000434104ae1a62fe09c5f51b13905f07f06b99a2f7159b2225f374cd378d71302fa28414e7aab37397f554a7df5f142c21c1b7303b8a0626f1baded5c72a704f7e6cd84cac00286bee0000000043410411db93e1dcdb8a016b49840f8c53bc1eb68a382e97b1482ecad7b148a6909a5cb2e0eaddfb84ccf9744464f82e160bfa9b8b64f9d4c03f999b8643f656b412a3ac00000000"));
		Tx t2 = new Tx ();
		t2.fromWire (reader);
		assertTrue (t2.getHash ().equals ("f4184fc596403b9d638783cf57adfe4c75c605f6356fbc91338530e9831e9e16"));
		t1.getInputs ().get (0).setSource (t2.getOutputs ().get (1));
		assertTrue (new Script (t1, 0).evaluate ());

		reader =
				new WireFormat.Reader (
						ByteUtils
								.fromHex ("0100000001944badc33f9a723eb1c85dde24374e6dee9259ef4cfa6a10b2fd05b6e55be400000000008c4930460221009f8aef83489d5c3524b68ddf77e8af8ceb5cba89790d31d2d2db0c80b9cbfd26022100bb2c13e15bb356a4accdd55288e8b2fd39e204a93d849ccf749eaef9d8162787014104f9804cfb86fb17441a6562b07c4ee8f012bdb2da5be022032e4b87100350ccc7c0f4d47078b06c9d22b0ec10bdce4c590e0d01aed618987a6caa8c94d74ee6dcffffffff0100f2052a010000001976a9146934efcef36903b5b45ebd1e5f862d1b63a99fa588ac00000000"));
		t1 = new Tx ();
		t1.fromWire (reader);
		assertTrue (t1.getHash ().equals ("74c1a6dd6e88f73035143f8fc7420b5c395d28300a70bb35b943f7f2eddc656d"));

		reader =
				new WireFormat.Reader (
						ByteUtils
								.fromHex ("01000000016d65dcedf2f743b935bb700a30285d395c0b42c78f3f143530f7886edda6c174000000008c493046022100b687c4436277190953466b3e4406484e89a4a4b9dbefea68cf5979f74a8ef5b1022100d32539ffb88736f3f9445fa6dd484b443ebb31af1471ee65071c7414e3ec007b014104f9804cfb86fb17441a6562b07c4ee8f012bdb2da5be022032e4b87100350ccc7c0f4d47078b06c9d22b0ec10bdce4c590e0d01aed618987a6caa8c94d74ee6dcffffffff0240420f000000000043410403c344438944b1ec413f7530aaa6130dd13562249d07d53ba96d8ac4f59832d05c837e36efd9533a6adf1920465fed2a4553fb357844f2e41329603c320753f4acc0aff62901000000434104f9804cfb86fb17441a6562b07c4ee8f012bdb2da5be022032e4b87100350ccc7c0f4d47078b06c9d22b0ec10bdce4c590e0d01aed618987a6caa8c94d74ee6dcac00000000"));
		t2 = new Tx ();
		t2.fromWire (reader);
		assertTrue (t2.getHash ().equals ("131f68261e28a80c3300b048c4c51f3ca4745653ba7ad6b20cc9188322818f25"));

		t2.getInputs ().get (0).setSource (t1.getOutputs ().get (0));
		assertTrue (new Script (t2, 0).evaluate ());

		reader =
				new WireFormat.Reader (
						ByteUtils
								.fromHex ("01000000017fd8dfdb54b5212c4e3151a39f4ffe279fd7f238d516a2ca731529c095d97449010000008b483045022100b6a7fe5eea81894bbdd0df61043e42780543457fa5581ac1af023761a098e92202201d4752785be5f9d1b9f8d362b8cf3b05e298a78c4abff874b838bb500dcf2a120141042e3c4aeac1ffb1c86ce3621afb1ca92773e02badf0d4b1c836eb26bd27d0c2e59ffec3d6ab6b8bbeca81b0990ab5224ebdd73696c4255d1d0c6b3c518a1a053effffffff01404b4c00000000001976a914dc44b1164188067c3a32d4780f5996fa14a4f2d988ac00000000"));
		t1 = new Tx ();
		t1.fromWire (reader);
		assertTrue (t1.getHash ().equals ("406b2b06bcd34d3c8733e6b79f7a394c8a431fbf4ff5ac705c93f4076bb77602"));

		reader =
				new WireFormat.Reader (
						ByteUtils
								.fromHex ("01000000010276b76b07f4935c70acf54fbf1f438a4c397a9fb7e633873c4dd3bc062b6b40000000008c493046022100d23459d03ed7e9511a47d13292d3430a04627de6235b6e51a40f9cd386f2abe3022100e7d25b080f0bb8d8d5f878bba7d54ad2fda650ea8d158a33ee3cbd11768191fd004104b0e2c879e4daf7b9ab68350228c159766676a14f5815084ba166432aab46198d4cca98fa3e9981d0a90b2effc514b76279476550ba3663fdcaff94c38420e9d5000000000100093d00000000001976a9149a7b0f3b80c6baaeedce0a0842553800f832ba1f88ac00000000"));
		t2 = new Tx ();
		t2.fromWire (reader); // this is the transaction with the wrong SIGHASH_ALL
		assertTrue (t2.getHash ().equals ("c99c49da4c38af669dea436d3e73780dfdb6c1ecf9958baa52960e8baee30e73"));

		t2.getInputs ().get (0).setSource (t1.getOutputs ().get (0));
		assertTrue (new Script (t2, 0).evaluate ());

		// S value negative
		t1 =
				Tx.fromWireDump ("0100000001289eb02e8ddc1ee3486aadc1cd1335fba22a8e3e87e3f41b7c5bbe7fb4391d81010000008a47304402206b5c3b1c86748dcf328b9f3a65e10085afcf5d1af5b40970d8ce3a9355e06b5b0220cdbdc23e6d3618e47056fccc60c5f73d1a542186705197e5791e97f0e6582a32014104f25ec495fa21ad14d69f45bf277129488cfb1a339aba1fed3c5099bb6d8e9716491a14050fbc0b2fed2963dc1e56264b3adf52a81b953222a2180d48b54d1e18ffffffff0140420f00000000001976a914e6ba8cc407375ce1623ec17b2f1a59f2503afc6788ac00000000");
		t2 =
				Tx.fromWireDump ("01000000014213d2fe8f942dd7a72df14e656baab0e8b2b7f59571771ddf170b588379a2b6010000008b483045022037183e3e47b23634eeebe6fd155f0adbde756bf00a6843a1317b6548a03f3cfe0221009f96bec8759837f844478a35e102618918662869188f99d32dffe6ef7f81427e014104a7d3b0dda6d4d0a44b137a65105cdfed890b09ce2d283d5683029f46a00e531bff1deb3ad3862e0648dca953a4250b83610c4f20861555a2f5638bd3d7aff93dffffffff02ddfb1100000000001976a9142256ff6b9b9fea32bfa8e64aed10ee695ffe100988ac40420f00000000001976a914c62301ef02dfeec757fb8dedb8a45eda5fb5ee4d88ac00000000");

		t1.getInputs ().get (0).setSource (t2.getOutputs ().get (1));
		assertTrue (new Script (t1, 0).evaluate ());

		// R value negative
		t1 =
				Tx.fromWireDump ("01000000024448a3999e6b39584d6acffbc620376d26ec88303913a137d286be0ea7c5931c000000008a473044022090f7346fa0f6a4dc4b31300bf93be229001a1104532829644e07f45814bb734e0220579da5a14362f46bfd7c2be0d19c67caedc812147b9b8d574e34a3932cf21f7a014104e9469f3c23309dd1eb3557ba2536ae7b58743425739c00c4af436998a0974d20edcb3c5a4cb621f103915df1271fdb56e58bd8161fbe24a726906328f48f9700ffffffff4448a3999e6b39584d6acffbc620376d26ec88303913a137d286be0ea7c5931c010000008a4730440220f7e67e0ffdd05f9c551bcf45ba94db0edb85907526ceece4d28269192edd082c0220cb5655b709086096412ffdfc0e3b8b74405da325a4701cfe2eddee41a3395982014104e9469f3c23309dd1eb3557ba2536ae7b58743425739c00c4af436998a0974d20edcb3c5a4cb621f103915df1271fdb56e58bd8161fbe24a726906328f48f9700ffffffff02a0c44a00000000001976a914623dbe779a29c6bc2615cd7bf5a35453f495e22988ac900e4d00000000001976a9149e969049aefe972e41aaefac385296ce18f3075188ac00000000");
		t2 =
				Tx.fromWireDump ("010000000104cc410a858127cad099f4ea6e1942a9a9002c14acc6d1bbbc223c8ec97e482a010000008a47304402207547807093f864090cb68a5913499ce75554404e8f47699bea33a78f2d63dabd0220706d44bfdf2c6e10a11b8c0b800eef5fb06ecaae60e2653a742c4b4d58436182014104e9469f3c23309dd1eb3557ba2536ae7b58743425739c00c4af436998a0974d20edcb3c5a4cb621f103915df1271fdb56e58bd8161fbe24a726906328f48f9700ffffffff02a0860100000000001976a9149e969049aefe972e41aaefac385296ce18f3075188ac904c9600000000001976a9149e969049aefe972e41aaefac385296ce18f3075188ac00000000");

		t1.getInputs ().get (0).setSource (t2.getOutputs ().get (0));
		assertTrue (new Script (t1, 0).evaluate ());

		// Multisig 1-1
		t1 =
				Tx.fromWireDump ("01000000024de8b0c4c2582db95fa6b3567a989b664484c7ad6672c85a3da413773e63fdb8000000006b48304502205b282fbc9b064f3bc823a23edcc0048cbb174754e7aa742e3c9f483ebe02911c022100e4b0b3a117d36cab5a67404dddbf43db7bea3c1530e0fe128ebc15621bd69a3b0121035aa98d5f77cd9a2d88710e6fc66212aff820026f0dad8f32d1f7ce87457dde50ffffffff4de8b0c4c2582db95fa6b3567a989b664484c7ad6672c85a3da413773e63fdb8010000006f004730440220276d6dad3defa37b5f81add3992d510d2f44a317fd85e04f93a1e2daea64660202200f862a0da684249322ceb8ed842fb8c859c0cb94c81e1c5308b4868157a428ee01ab51210232abdc893e7f0631364d7fd01cb33d24da45329a00357b3a7886211ab414d55a51aeffffffff02e0fd1c00000000001976a914380cb3c594de4e7e9b8e18db182987bebb5a4f7088acc0c62d000000000017142a9bc5447d664c1d0141392a842d23dba45c4f13b17500000000");
		t2 =
				Tx.fromWireDump ("01000000017ea56cd68c74b4cd1a2f478f361b8a67c15a6629d73d95ef21d96ae213eb5b2d010000006a4730440220228e4deb3bc5b47fc526e2a7f5e9434a52616f8353b55dbc820ccb69d5fbded502206a2874f7f84b20015614694fe25c4d76f10e31571f03c240e3e4bbf1f9985be201210232abdc893e7f0631364d7fd01cb33d24da45329a00357b3a7886211ab414d55affffffff0230c11d00000000001976a914709dcb44da534c550dacf4296f75cba1ba3b317788acc0c62d000000000017142a9bc5447d664c1d0141392a842d23dba45c4f13b17500000000");

		t1.getInputs ().get (1).setSource (t2.getOutputs ().get (1));
		assertTrue (new Script (t1, 1).evaluate ());

		// Multisig 3-1
		t1 =
				Tx.fromWireDump ("010000000290c5e425bfba62bd5b294af0414d8fa3ed580c5ca6f351ccc23e360b14ff7f470100000091004730440220739d9ab2c3e7089e7bd311f267a65dc0ea00f49619cb61ec016a5038016ed71202201b88257809b623d471e429787c36e0a9bcd2a058fc0c75fd9c25f905657e3b9e01ab512103c86390eb5230237f31de1f02e70ce61e77f6dbfefa7d0e4ed4f6b3f78f85d8ec2103193f28067b502b34cac9eae39f74dba4815e1278bab31516efb29bd8de2c1bea52aeffffffffdd7f3ce640a2fb04dbe24630aa06e4299fbb1d3fe585fe4f80be4a96b5ff0a0d01000000b400483045022100a28d2ace2f1cb4b2a58d26a5f1a2cc15cdd4cf1c65cee8e4521971c7dc60021c0220476a5ad62bfa7c18f9174d9e5e29bc0062df543e2c336ae2c77507e462bbf95701ab512103c86390eb5230237f31de1f02e70ce61e77f6dbfefa7d0e4ed4f6b3f78f85d8ec2103193f28067b502b34cac9eae39f74dba4815e1278bab31516efb29bd8de2c1bea21032462c60ebc21f4d38b3c4ccb33be77b57ae72762be12887252db18fd6225befb53aeffffffff02e0fd1c00000000001976a9148501106ab5492387998252403d70857acfa1586488ac50c3000000000000171499050637f553f03cc0f82bbfe98dc99f10526311b17500000000");
		t2 =
				Tx.fromWireDump ("0100000001eae7c33c5a3ad25316a4a1a0220343693077d7a35c6d242ed731d9f26c9f8b45010000006b48304502205b910ff27919bb4b81847e17e19848a8148373b5d84856e8a0798395c1a4df6e022100a9300a11b37b52997726dab17851914151bd647ca053d60a013b8e0ad42d1c6e012102b2e1e38d1b15170212a852f68045979d790814a139ed57bffba3763f75e18808ffffffff02b0453c00000000001976a914c39c8d989dfdd7fde0ee80be36113c5abcefcb9c88ac40420f0000000000171464d63d835705618da2111ca3194f22d067187cf2b17500000000");

		t1.getInputs ().get (0).setSource (t2.getOutputs ().get (1));
		assertTrue (new Script (t1, 0).evaluate ());

		// FROM TESTNET3
		t1 =
				Tx.fromWireDump ("010000000560e0b5061b08a60911c9b2702cc0eba80adbe42f3ec9885c76930837db5380c001000000054f01e40164ffffffff0d2fe5749c96f15e37ceed29002c7f338df4f2781dd79f4d4eea7a08aa69b959000000000351519bffffffff0d2fe5749c96f15e37ceed29002c7f338df4f2781dd79f4d4eea7a08aa69b959020000000452018293ffffffff0d2fe5749c96f15e37ceed29002c7f338df4f2781dd79f4d4eea7a08aa69b95903000000045b5a5193ffffffff0d2fe5749c96f15e37ceed29002c7f338df4f2781dd79f4d4eea7a08aa69b95904000000045b5a5193ffffffff06002d310100000000029f91002d3101000000000401908f87002d31010000000001a0002d3101000000000705feffffff808730d39700000000001976a9140467f85e06a2ef0a479333b47258f4196fb94b2c88ac002d3101000000000604ffffff7f9c00000000");

		t2 =
				Tx.fromWireDump ("01000000017ae001aef566f8273a7cd14dcbc1d2bcd7927de792c5042375033991ef5523c3000000006a47304402200bb34283458a6f141fbea8dd9c3f4db0abb8dea2282364821886e60f313be94502201198397af91be19622e8ec1e52e2e7cb5ce21640d490f1ca7fb22452e01e1fee012103e4d7f9492784fc6b3439607be821148e0d4f11ca35de74521d277500203492eaffffffff024073a574000000001976a91457722497e036129a643d767d3a9559b9dea58d0788ac809698000000000001a500000000");

		t1.getInputs ().get (0).setSource (t2.getOutputs ().get (1));
		System.out.println (new Script (t1, 0).toReadableConnected ());
		System.out.println (new Script (t1, 0).dumpConnected ());
		assertTrue (new Script (t1, 0).evaluate ());
	}
}
