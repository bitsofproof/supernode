package org.purser.server;

import hu.blummers.bitcoin.core.Chain;

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
			BlockStoreDao blockStore = context.getBean (BlockStoreDao.class);
			blockStore.setChain (Chain.production);
			blockStore.resetStore();
			
		} catch (Exception e) {
			log.error("ChainDownloader", e);
		}
	}

}
