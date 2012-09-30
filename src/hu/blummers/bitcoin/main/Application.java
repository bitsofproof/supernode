package hu.blummers.bitcoin.main;

import java.io.IOException;

import hu.blummers.bitcoin.core.BitcoinNetwork;
import hu.blummers.bitcoin.core.ChainLoader;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Application {
	@Autowired
	BitcoinNetwork network;
	
	@Autowired
	ChainLoader chainLoader;
	
	public void start () throws IOException
	{
		network.start();
		chainLoader.start();
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
