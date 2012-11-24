/**
 * 
 */
package com.bitsofproof.supernode.main;

import java.io.IOException;

import org.kohsuke.args4j.CmdLineException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.GenericXmlApplicationContext;
import org.springframework.core.io.support.ResourcePropertySource;

import com.google.common.base.Enums;

public class Main
{
    public interface App
    {
        public void start(String[] args) throws Exception;
    }

    private static final Logger log = LoggerFactory.getLogger (Main.class);

    public static void main(String[] args) throws Exception
    {
        Profile profile = null;
        if (args.length > 0 && args[0] != "help")
            profile = Enums.valueOfFunction (Profile.class).apply (args[0].toUpperCase ());
        
        if (profile == null) {
            printUsage ();
            return;
        }
        
        try
        {
            loadContext (profile).getBean (App.class).start (args);
        }
        catch (CmdLineException cle)
        {
            cle.getParser ().printUsage (System.err);
        }
    }

    private static void printUsage() {
        System.err.println ("Usage: java com.bitsofproof.Supernode < demo | server | audit > [COMMAND ARGS]");
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
}
