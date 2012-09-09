package hu.blummers.bitcoin.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MessageFactory {
	private static final Logger log = LoggerFactory.getLogger(MessageFactory.class);
	
	public static BitcoinMessage createMessage (Chain chain, String command)
	{
		if ( command.equals("version") )
			return new VersionMessage (chain);
		else if ( command.equals("verack") )
			return new VerackMessage (chain);
		else if ( command.equals("inv") )
			return new InvMessage (chain);
		else if ( command.equals("addr") )
			return new AddrMessage (chain);
		else if ( command.equals("getdata") )
			return new GetDataMessage(chain);
		else if ( command.equals("getblocks") )
			return new GetBlocksMessage (chain);
		else if ( command.equals("block") )
			return new BlockMessage (chain);
		else if ( command.equals("alert") )
			return new AlertMessage (chain);

		log.info("unknown message " + command);
		return null;
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
