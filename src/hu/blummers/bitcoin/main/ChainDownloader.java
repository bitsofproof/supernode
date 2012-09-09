package hu.blummers.bitcoin.main;

import hu.blummers.bitcoin.core.BitcoinNetwork;
import hu.blummers.bitcoin.core.BitcoinPeer;
import hu.blummers.bitcoin.core.Chain;
import hu.blummers.bitcoin.messages.BitcoinMessage;
import hu.blummers.bitcoin.messages.BitcoinMessageListener;
import hu.blummers.bitcoin.messages.BlockMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class ChainDownloader {
    private static final Logger log = LoggerFactory.getLogger(ChainDownloader.class);


	public static void main(String[] args) {
		try {
			Setup.setup();
			ApplicationContext context = 
		            new ClassPathXmlApplicationContext("app-context.xml");
			
			log.info("Chaindownloader starts");
			BitcoinNetwork network = new BitcoinNetwork (Chain.production);
			network.start();
			network.discover();
			/*
			network.downloadBlockChain(Chain.production.getGenesis().getHash(), new BitcoinMessageListener (){
				@Override
				public void process(BitcoinMessage m, BitcoinPeer peer) {
					BlockMessage bm = (BlockMessage)m;
					log.info("got block " + bm.getBlock().getHash());
				}});
				*/
			synchronized ( log )
			{
				log.wait ();
			}
		} catch (Exception e) {
			log.error("ChainDownloader", e);
		}
	}

}
