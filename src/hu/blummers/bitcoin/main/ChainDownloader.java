package hu.blummers.bitcoin.main;

import hu.blummers.bitcoin.core.BitcoinNetwork;
import hu.blummers.bitcoin.core.BitcoinPeer;
import hu.blummers.bitcoin.core.Chain;
import hu.blummers.bitcoin.core.ChainLoader;
import hu.blummers.bitcoin.core.ChainStore;
import hu.blummers.bitcoin.core.UnconfirmedTransactions;
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
			log.info("Authorisation");
			Setup.setup();
			log.info("Spring context setup");
			ApplicationContext context = 
		            new ClassPathXmlApplicationContext("app-context.xml");
			
			log.info("Chaindownloader starts");

			ChainStore chainStore = context.getBean (ChainStore.class);
			/*
			log.info("Reset store");						
			chainStore.resetStore(Chain.production);
			*/
			
			log.info("Connect to bitcoin network");			
			BitcoinNetwork network = new BitcoinNetwork (Chain.production);
			network.start();
			network.discover();
			
			UnconfirmedTransactions unconfirmed = new UnconfirmedTransactions (network);
			unconfirmed.start();
			
			/*
			log.info("Start chainloader");
			ChainLoader loader = new ChainLoader (network, chainStore);
			loader.start();
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
