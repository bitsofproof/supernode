package com.bitsofproof.supernode.api;


public class SimpleWalletFactory implements WalletFactory
{

	private BCSAPI api;

	public void setApi (BCSAPI api)
	{
		this.api = api;
	}

	@Override
	public Wallet getWallet (String fileName, String passphrase) throws BCSAPIException
	{
		SerializedWallet wallet = SerializedWallet.read (fileName, passphrase, api.isProduction ());
		wallet.setApi (api);
		return wallet;
	}

}
