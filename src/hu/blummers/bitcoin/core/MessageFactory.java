package hu.blummers.bitcoin.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MessageFactory {
	private static final Logger log = LoggerFactory.getLogger(MessageFactory.class);
	
	public static Message createMessage (Chain chain, String command)
	{
		if ( command.equals("version") )
			return createVersionMessage (chain);
		if ( command.equals("verack") )
			return createVerackMessage (chain);
		if ( command.equals("inv") )
			return createInvMessage (chain);
		
		log.info("unkwon message type received: "+ command);
		return null;
	}
	
	public static VersionMessage createVersionMessage (Chain chain)
	{
		return new VersionMessage (chain, "version");
	}
	
	public static Message createVerackMessage (Chain chain)
	{
		return new Message (chain, "verack");
	}
	
	public static Message createInvMessage (Chain chain)
	{
		return new InvMessage (chain, "inv");
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
