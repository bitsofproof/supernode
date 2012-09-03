package hu.blummers.bitcoin.core;

public class MessageFactory {
	public static Message getMessage (Chain chain, String command, WireFormat.Reader reader)
	{
		Message m = null;
		if ( command.equals("version") )
			m = new VersionMessage ();
		
		if ( m.getCommand().equals(command) )
			throw new RuntimeException ("Envelope failed to create the right message");
		
		m.fromWire(reader);
		return m;
	}
	
	/*
	 *         if (command.equals("version")) {
            return new VersionMessage(params, payloadBytes);
        } else if (command.equals("inv")) {
            message = new InventoryMessage(params, payloadBytes, parseLazy, parseRetain, length);
        } else if (command.equals("block")) {
            message = new Block(params, payloadBytes, parseLazy, parseRetain, length);
        } else if (command.equals("getdata")) {
            message = new GetDataMessage(params, payloadBytes, parseLazy, parseRetain, length);
        } else if (command.equals("tx")) {
            Transaction tx = new Transaction(params, payloadBytes, null, parseLazy, parseRetain, length);
            if (hash != null)
                tx.setHash(new Sha256Hash(Utils.reverseBytes(hash)));
            message = tx;
        } else if (command.equals("addr")) {
            message = new AddressMessage(params, payloadBytes, parseLazy, parseRetain, length);
        } else if (command.equals("ping")) {
            return new Ping();
        } else if (command.equals("verack")) {
            return new VersionAck(params, payloadBytes);
        } else if (command.equals("headers")) {
            return new HeadersMessage(params, payloadBytes);
        } else if (command.equals("alert")) {
            return new AlertMessage(params, payloadBytes);
        } else {
            log.warn("No support for deserializing message with name {}", command);
            return new UnknownMessage(params, command, payloadBytes);
        }

			*/

}
