/**
 * 
 */
package com.bitsofproof;

import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.GenericXmlApplicationContext;

import com.google.common.collect.Lists;

/**
 * @author Bártfai Tamás <bartfaitamas@gmail.com>
 * 
 */
public class Supernode
{
    public interface App
    {
        public void start(String[] args) throws Exception;
    }

    private static final Logger log = LoggerFactory.getLogger (Supernode.class);

    public static void main(String[] args) throws Exception
    {
        Options options = Options.parse (args);
        if (options == null)
            return;

        GenericXmlApplicationContext ctx = loadContext (options.profile);
        App app = ctx.getBean (App.class);
        app.start (options.arguments.toArray (new String[0]));

    }

    private static GenericXmlApplicationContext loadContext(Profiles profile)
    {
        log.info ("bitsofproof supernode (c) 2012 Tamas Blummer tamas@bitsofproof.com");
        log.trace ("Spring context setup");

        GenericXmlApplicationContext ctx = new GenericXmlApplicationContext ();
        ctx.getEnvironment ().setActiveProfiles (profile.name ().toLowerCase ());
        ctx.load ("classpath:context/*-config.xml");
        ctx.refresh ();

        log.info ("Profile: " + profile.name());
        return ctx;
    }

    private static enum Profiles
    {
        DEMO, SERVER
    }

    private static class Options
    {

        @Argument(required = true, metaVar = "<COMMAND>", usage = "DEMO or SERVER", index = 0)
        Profiles profile;

        @Argument(required = false, metaVar = "<COMMAND ARGS>", usage = "Command specific arguments", multiValued = true, index = 1)
        List<String> arguments = Lists.newArrayList();

        public static Options parse(String[] args)
        {
            Options options = new Options ();
            CmdLineParser parser = new CmdLineParser (options);
            try
            {
                parser.parseArgument (args);
            }
            catch (Exception e)
            {
                System.err.println ("Usage: java com.bitsofproof.Supernode <COMMAND> [COMMAND ARGS]");
                parser.printUsage (System.err);
                return null;
            }
            return options;
        }

    }

}
