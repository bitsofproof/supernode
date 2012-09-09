package hu.blummers.bitcoin.messages;

import java.util.ArrayList;
import java.util.List;

import hu.blummers.bitcoin.core.Chain;
import hu.blummers.bitcoin.core.WireFormat;
import hu.blummers.bitcoin.core.WireFormat.Address;
import hu.blummers.bitcoin.core.WireFormat.Reader;
import hu.blummers.bitcoin.core.WireFormat.Writer;

public class AdrMessage extends BitcoinMessage {

	private List<WireFormat.Address> addresses = new ArrayList<WireFormat.Address> ();
	
	public AdrMessage(Chain chain) {
		super(chain, "addr");
	}

	@Override
	public void toWire(Writer writer) {
		writer.writeVarInt(addresses.size());
		for ( WireFormat.Address a : addresses )
		{
			writer.writeAddress(a, getChain ().getVersion(), false);
		}
	}

	@Override
	public void fromWire(Reader reader, long version) {
		long n = reader.readVarInt();
		for ( long i = 0; i < n; ++i )
		{
			addresses.add(reader.readAddress(version, false));
		}
	}

	public List<WireFormat.Address> getAddresses() {
		return addresses;
	}

	public void setAddresses(List<WireFormat.Address> addresses) {
		this.addresses = addresses;
	}

}
