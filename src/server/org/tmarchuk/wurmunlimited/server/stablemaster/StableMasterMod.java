package org.tmarchuk.wurmunlimited.server.stablemaster;

/**
 * Created by Tyson Marchuk on 2016-04-27.
 */

// From Wurm Unlimited Server

// From Ago's modloader
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.ItemTemplatesCreatedListener;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmMod;

// Base Java
import java.util.logging.Level;
import java.util.logging.Logger;

public class StableMasterMod implements WurmMod, Initable, ServerStartedListener, ItemTemplatesCreatedListener
{
    private static final Logger logger = Logger.getLogger(StableMasterMod.class.getName());

    public static void logException(String msg, Throwable e)
    {
        if (logger != null)
            logger.log(Level.SEVERE, msg, e);
    }
    @Override
    public void onItemTemplatesCreated() 
    {}

    @Override
    public void onServerStarted()
    {}

    @Override
    public void init() 
    {
    }
}
