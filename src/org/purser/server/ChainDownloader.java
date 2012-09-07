package org.purser.server;

import hu.blummers.bitcoin.core.Chain;
import hu.blummers.bitcoin.core.Peers;

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
			Peers peers = new Peers (Chain.production);
			peers.discover(Chain.production);
			peers.start();
			Thread.sleep(1000*1000);
		} catch (Exception e) {
			log.error("ChainDownloader", e);
		}
	}

}
