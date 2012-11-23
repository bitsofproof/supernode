/**
 * 
 */
package com.bitsofproof.supernode.main;

import java.io.IOException;
import java.util.List;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.GenericXmlApplicationContext;
import org.springframework.core.io.support.ResourcePropertySource;

import com.google.common.collect.Lists;

public class Main
{
    public interface App
    {
        public void start(String[] args) throws Exception;
    }

    private static final Logger log = LoggerFactory.getLogger (Main.class);

    public static void main(String[] args) throws Exception
    {
        Options options = Options.parse (args);
        if (options == null)
            return;

        GenericXmlApplicationContext ctx = loadContext (options.profile);
        App app = ctx.getBean (App.class);
        app.start (options.arguments.toArray (new String[0]));

    }

    private static GenericXmlApplicationContext loadContext(Profile profile) throws IOException
    {
        log.info ("bitsofproof supernode (c) 2012 Tamas Blummer tamas@bitsofproof.com");
        log.trace ("Spring context setup");
        log.info ("Profile: " + profile.toString ());

        GenericXmlApplicationContext ctx = new GenericXmlApplicationContext ();
        ctx.getEnvironment ().getPropertySources ().addFirst (loadProperties (profile));
        ctx.getEnvironment ().setActiveProfiles (profile.toString ());
        ctx.load ("classpath:context/common-context.xml");
        ctx.load ("classpath:context/*-config.xml");
        ctx.refresh ();

        return ctx;
    }

    /**
     * @param profile
     * @return
     * @throws IOException
     */
    private static ResourcePropertySource loadProperties(Profile profile) throws IOException
    {
        String propertiesLocation = String.format ("classpath:etc/supernode-%s.properties", profile.toString ());
        return new ResourcePropertySource (propertiesLocation);
    }
    
    

    static enum Profile
    {
        DEMO, AUDIT, SERVER;
        
        @Override
        public String toString()
        {
            return super.toString ().toLowerCase ();
        }
    }

    static class Options
    {

        @Argument(required = true, metaVar = "<COMMAND>", usage = "DEMO or SERVER", index = 0)
        Profile profile;

        @Argument(required = false, metaVar = "<COMMAND ARGS>", usage = "Command specific arguments", multiValued = true, index = 1)
        List<String> arguments = Lists.newArrayList ();

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
