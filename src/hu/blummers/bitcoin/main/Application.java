package hu.blummers.bitcoin.main;

import java.io.IOException;

import hu.blummers.bitcoin.core.BitcoinNetwork;
import hu.blummers.bitcoin.core.Chain;
import hu.blummers.bitcoin.core.ChainLoader;
import hu.blummers.bitcoin.core.ChainStore;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

@Component
public class Application {
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
	
	public void start (ApplicationContext context) throws IOException
	{
		this.context = context;
		BitcoinNetwork network = new BitcoinNetwork (transactionManager, chain, store);
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
