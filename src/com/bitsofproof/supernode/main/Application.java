package com.bitsofproof.supernode.main;

import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.openjpa.lib.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import com.bitsofproof.supernode.core.BitcoinNetwork;
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.ChainLoader;
import com.bitsofproof.supernode.core.P2P;
import com.bitsofproof.supernode.model.ChainStore;

@Component
public class Application {
	private static final Logger log = LoggerFactory.getLogger(Application.class);

	@Autowired
	private Chain chain;
	
	@Autowired
	private ChainStore store;
	
	@Autowired
	PlatformTransactionManager transactionManager;

	private ApplicationContext context;
	
	public ApplicationContext getContext ()
	{
		return context;
	}
	
	public void start (ApplicationContext context, String [] args) throws IOException
	{
		final CommandLineParser parser = new GnuParser ();
		final Options gnuOptions = new Options();  
		   gnuOptions.addOption("h", "help", false, "I cant help you")  
		             .addOption("c", "connections", true, "Number of connections")
		             .addOption("r", "resetdb", false, "initialize database")
		             ;  

			final CommandLine cl;
			try {
				cl = parser.parse(gnuOptions, args);
			} catch (ParseException e1) {
				log.error("Invalid options ", e1);
				return;
			}
			int connections = 10;
			
			if ( cl.hasOption("connections") )
				connections = Integer.valueOf(cl.getOptionValues("connections")[0].toString());

			new TransactionTemplate (transactionManager).execute (new TransactionCallbackWithoutResult() {
			
			@Override
			protected void doInTransactionWithoutResult(TransactionStatus status) {
				if ( cl.hasOption("resetdb"))
					store.resetStore(chain);
				store.cache();
			}
		});
		this.context = context;
		BitcoinNetwork network = new BitcoinNetwork (transactionManager, chain, store, connections);
		network.start();
		ChainLoader loader = new ChainLoader (transactionManager, network, store);
		loader.start();
		synchronized ( this )
		{
			try {
				wait ();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
