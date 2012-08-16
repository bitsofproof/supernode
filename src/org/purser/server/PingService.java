package org.purser.server;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.Date;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.google.bitcoin.core.AbstractWalletEventListener;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.PeerAddress;
import com.google.bitcoin.core.PeerGroup;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.StoredBlock;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.TransactionInput;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.discovery.DnsDiscovery;
import com.google.bitcoin.store.BlockStoreException;

public class PingService {

	
	
	public static void main(String[] args) throws IOException {		
		
		try {
			Setup.setup();
			ApplicationContext context = 
		            new ClassPathXmlApplicationContext("app-context.xml");

			
	        String peerHost = args.length > 0 ? args[0] : null;
	        int peerPort = args.length > 1 ? Integer.parseInt(args[1]) : NetworkParameters.prodNet().port;
			
			boolean testNet = args.length > 0
					&& args[0].equalsIgnoreCase("testnet");
			final NetworkParameters params = testNet ? NetworkParameters
					.testNet() : NetworkParameters.prodNet();
			String suffix = testNet ? "testnet" : "prodnet";
			String filePrefix = "pingservice-" + suffix;

			// Try to read the wallet from storage, create a new one if not
			// possible.
			Wallet wallet;
			final File walletFile = new File(filePrefix + ".wallet");
			try {
				wallet = Wallet.loadFromFile(walletFile);
			} catch (IOException e) {
				wallet = new Wallet(params);
				wallet.keychain.add(new ECKey());
				wallet.saveToFile(walletFile);
			}
			// Fetch the first key in the wallet (should be the only key).
			ECKey key = wallet.keychain.get(0);

			// Load the block chain, if there is one stored locally.
			System.out.println("Reading block store from disk");
			long time = System.currentTimeMillis();
			
			BlockStoreDao blockStore = context.getBean (BlockStoreDao.class);
			blockStore.setNetworkParams(params);
			blockStore.resetStore();
			
			System.out.println("Opened block store in "
					+ (System.currentTimeMillis() - time) + " ms");

			
	        // Connect to the localhost node. One minute timeout since we won't try any other peers
	        System.out.println("Connecting ...");
	        BlockChain chain = new BlockChain(params, wallet, blockStore);

	        final PeerGroup peerGroup = new PeerGroup(params, chain);
	        // Set some version info.
	        peerGroup.setUserAgent("PingService", "1.0");
	        // Download headers only until a day ago.
	        peerGroup.setFastCatchupTimeSecs((new Date().getTime() / 1000) - (60 * 60 * 24));
	        if (peerHost != null) {
	            peerGroup.addAddress(new PeerAddress(InetAddress.getByName(peerHost), peerPort));
	        } else {
	            peerGroup.addPeerDiscovery(new DnsDiscovery(params));
	        }

	        peerGroup.addWallet(wallet);
	        peerGroup.start();

			
			
			
			
			// We want to know when the balance changes.
			wallet.addEventListener(new AbstractWalletEventListener() {
				@Override
				public void onCoinsReceived(Wallet w, Transaction tx,
						BigInteger prevBalance, BigInteger newBalance) {
					// Running on a peer thread.
					assert !newBalance.equals(BigInteger.ZERO);
					// It's impossible to pick one specific identity that you
					// receive coins from in BitCoin as there
					// could be inputs from many addresses. So instead we just
					// pick
					// the first and assume they were all
					// owned by the same person.
					try {
						TransactionInput input = tx.getInputs().get(0);
						Address from = input.getFromAddress();
						BigInteger value = tx.getValueSentToMe(w);
						System.out.println("Received "
								+ Utils.bitcoinValueToFriendlyString(value)
								+ " from " + from.toString());
						// Now send the coins back!
						Transaction sendTx = w
								.sendCoins(peerGroup, from, value);
						assert sendTx != null; // We should never try to send
												// more
												// coins than we have!
						System.out
								.println("Sent coins back! Transaction hash is "
										+ sendTx.getHashAsString());
						w.saveToFile(walletFile);
					} catch (ScriptException e) {
						// If we didn't understand the scriptSig, just crash.
						e.printStackTrace();
						throw new RuntimeException(e);
					} catch (IOException e) {
						e.printStackTrace();
						throw new RuntimeException(e);
					}
				}
			});

			peerGroup.downloadBlockChain();

			System.out.println("Send coins to: "
					+ key.toAddress(params).toString());
			System.out
					.println("Waiting for coins to arrive. Press Ctrl-C to quit.");
			// The peer thread keeps us alive until something kills the process.
		} catch (Exception e) {
			System.err.println(e);
		}
	}

	/**
	 * @param blockStore
	 * @throws BlockStoreException
	 */
	static void iterateAll(BlockStoreDaoImpl blockStore) throws BlockStoreException {
		long time = System.currentTimeMillis();
		StoredBlock block = blockStore.getChainHead();
		int count = 0;
		while (block != null) {
			count++;
			if (count % 1000 == 0)
				System.out.println("iterated " + count);
			block = block.getPrev(blockStore);
		}
		System.out.println("iterated " + count);
		System.out.println("Iterated block store in "
				+ (System.currentTimeMillis() - time) + " ms");
	}

}
