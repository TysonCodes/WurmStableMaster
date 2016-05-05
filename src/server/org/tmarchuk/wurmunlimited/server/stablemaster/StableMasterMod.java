package org.tmarchuk.wurmunlimited.server.stablemaster;

/**
 * Created by Tyson Marchuk on 2016-04-27.
 */

// From Wurm Common
import com.wurmonline.shared.constants.ItemMaterials;

// From Wurm Unlimited Server
import com.wurmonline.server.behaviours.BehaviourList;
import com.wurmonline.server.items.ItemTemplateFactory;
import static com.wurmonline.server.items.ItemTypes.*;
import com.wurmonline.server.MiscConstants;

// From Ago's modloader
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.ItemTemplatesCreatedListener;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;
import org.gotti.wurmunlimited.modsupport.IdFactory;
import org.gotti.wurmunlimited.modsupport.IdType;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;
import org.gotti.wurmunlimited.modsupport.creatures.ModCreatures;

// Base Java
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StableMasterMod implements WurmServerMod, Configurable, Initable, PreInitable, ServerStartedListener, ItemTemplatesCreatedListener
{
	private static final Logger logger = Logger.getLogger(StableMasterMod.class.getName());
	
	// Constants
	private static final String MOUNT_TOKEN_IDENTIFIER = "mountToken";
	private static final int MOUNT_TOKEN_SIZE = 3;	// Normal as compared to 1 = tiny, 2 = small, 4 = large, 5 = huge
	private static final short MOUNT_TOKEN_IMAGE_NUMBER = 321; // Piece of paper.
	private static final float MOUNT_TOKEN_DIFFICULTY = 1.0f;
	private static final int MOUNT_TOKEN_VALUE = 0;
	private static final boolean MOUNT_TOKEN_IS_PURCHASED = false;
	private static final int MOUNT_TOKEN_ARMOR_TYPE = -1;
	
	// Configuration value string constants
	private static final String CONFIG_SPECIFY_STABLE_MASTER_ID = "specifyStableMasterId";
	private static final String CONFIG_STABLE_MASTER_ID = "stableMasterId";
	private static final String CONFIG_SPECIFY_MOUNT_TOKEN_ID = "specifyMountTokenId";
	private static final String CONFIG_MOUNT_TOKEN_ID = "mountTokenId";
	private static final String CONFIG_MOUNT_TOKEN_CM_X = "mountTokenCentimetersX";
	private static final String CONFIG_MOUNT_TOKEN_CM_Y = "mountTokenCentimetersY";
	private static final String CONFIG_MOUNT_TOKEN_CM_Z = "mountTokenCentimetersZ";
	private static final String CONFIG_MOUNT_TOKEN_MIN_WEIGHT_GRAMS = "mountTokenMinimumWeightGrams";
	private static final String CONFIG_MOUNT_TOKEN_MAX_WEIGHT_GRAMS = "mountTokenMaximumWeightGrams";
	private static final String CONFIG_EXCHANGE_MOUNT_COST_IRONS = "exchangeMountCostIrons";
	private static final String CONFIG_ENABLE_NO_NPC_EXCHANGE = "enableNoNpcExchange";
	private static final String CONFIG_ENABLE_SMALL_BOATS_LOAD = "enableSmallBoatsLoad";
	
	// Configuration values
	private boolean specifyStableMasterId = false;
	private int stableMasterId = 20001;
	private boolean specifyMountTokenId = false;
	private int mountTokenId = 20002;
	private int mountTokenCentimetersX = 20;
	private int mountTokenCentimetersY = 50;
	private int mountTokenCentimetersZ = 200;
	private int mountTokenMinimumWeightGrams = 50000;
	private int mountTokenMaximumWeightGrams = 75000;
	private int exchangeMountCostIrons = 1234;
	private boolean enableNoNpcExchange = false;
	private boolean enableSmallBoatsLoad = false;
	
	// Internal
	private StableMaster stableMasterBuilder = null;
	
	public static void logException(String msg, Throwable e)
	{
		if (logger != null)
			logger.log(Level.SEVERE, msg, e);
	}

	@Override
	public void configure(Properties properties)
	{
		try
		{
			// Whether or not to use a hard coded stable master creature template ID.
			this.specifyStableMasterId = Boolean.parseBoolean(properties.getProperty(CONFIG_SPECIFY_STABLE_MASTER_ID, 
				String.valueOf(this.specifyStableMasterId)));
			logger.log(Level.INFO, CONFIG_SPECIFY_STABLE_MASTER_ID + ": " + this.specifyStableMasterId);
			
			// Hard coded stable master ID to use if enabled.
			this.stableMasterId = Integer.parseInt(properties.getProperty(CONFIG_STABLE_MASTER_ID, String.valueOf(this.stableMasterId)));
			logger.log(Level.INFO, CONFIG_STABLE_MASTER_ID + ": " + this.stableMasterId);
			
			// Whether or not to use a hard coded mount token item template ID.
			this.specifyMountTokenId = Boolean.parseBoolean(properties.getProperty(CONFIG_SPECIFY_MOUNT_TOKEN_ID, 
					String.valueOf(this.specifyMountTokenId)));
			logger.log(Level.INFO, CONFIG_SPECIFY_MOUNT_TOKEN_ID + ": " + this.specifyMountTokenId);
				
			// Hard coded mount token ID to use if enabled.
			this.mountTokenId = Integer.parseInt(properties.getProperty(CONFIG_MOUNT_TOKEN_ID, String.valueOf(this.mountTokenId)));
			logger.log(Level.INFO, CONFIG_MOUNT_TOKEN_ID + ": " + this.mountTokenId);
			
			// Mount token centimeters in the X dimension. Used to determine volume of token for loading.
			this.mountTokenCentimetersX = Integer.parseInt(properties.getProperty(CONFIG_MOUNT_TOKEN_CM_X, 
					String.valueOf(this.mountTokenCentimetersX)));
			logger.log(Level.INFO, CONFIG_MOUNT_TOKEN_CM_X + ": " + this.mountTokenCentimetersX);
			
			// Mount token centimeters in the Y dimension. Used to determine volume of token for loading.
			this.mountTokenCentimetersY = Integer.parseInt(properties.getProperty(CONFIG_MOUNT_TOKEN_CM_Y, 
					String.valueOf(this.mountTokenCentimetersY)));
			logger.log(Level.INFO, CONFIG_MOUNT_TOKEN_CM_Y + ": " + this.mountTokenCentimetersY);
			
			// Mount token centimeters in the Z dimension. Used to determine volume of token for loading.
			this.mountTokenCentimetersZ = Integer.parseInt(properties.getProperty(CONFIG_MOUNT_TOKEN_CM_Z, 
					String.valueOf(this.mountTokenCentimetersZ)));
			logger.log(Level.INFO, CONFIG_MOUNT_TOKEN_CM_Z + ": " + this.mountTokenCentimetersZ);
			
			// Mount token minimum weight in grams. Applied before maximum so if larger than maximum it will be ignored.
			this.mountTokenMinimumWeightGrams = Integer.parseInt(properties.getProperty(CONFIG_MOUNT_TOKEN_MIN_WEIGHT_GRAMS, 
					String.valueOf(this.mountTokenMinimumWeightGrams)));
			logger.log(Level.INFO, CONFIG_MOUNT_TOKEN_MIN_WEIGHT_GRAMS + ": " + this.mountTokenMinimumWeightGrams);
			
			// Mount token maximum weight in grams. Applied after minimum so, if smaller than minimum, 
			// minimum will be ignored.
			this.mountTokenMaximumWeightGrams = Integer.parseInt(properties.getProperty(CONFIG_MOUNT_TOKEN_MAX_WEIGHT_GRAMS, 
					String.valueOf(this.mountTokenMaximumWeightGrams)));
			logger.log(Level.INFO, CONFIG_MOUNT_TOKEN_MAX_WEIGHT_GRAMS + ": " + this.mountTokenMaximumWeightGrams);
	
			// Exchange mount cost in irons if using NPC.
			this.exchangeMountCostIrons = Integer.parseInt(properties.getProperty(CONFIG_EXCHANGE_MOUNT_COST_IRONS, 
					String.valueOf(this.exchangeMountCostIrons)));
			logger.log(Level.INFO, CONFIG_EXCHANGE_MOUNT_COST_IRONS + ": " + this.exchangeMountCostIrons);
			
			// Whether or not to allow the exchange action to work directly on a horse for no cost.
			this.enableNoNpcExchange = Boolean.parseBoolean(properties.getProperty(CONFIG_ENABLE_NO_NPC_EXCHANGE, 
				String.valueOf(this.enableNoNpcExchange)));
			logger.log(Level.INFO, CONFIG_ENABLE_NO_NPC_EXCHANGE + ": " + this.enableNoNpcExchange);
			
			// Whether or not to allow loading of mount tokens onto smaller boats (rowboat/sailboat).
			this.enableSmallBoatsLoad = Boolean.parseBoolean(properties.getProperty(CONFIG_ENABLE_SMALL_BOATS_LOAD, 
				String.valueOf(this.enableSmallBoatsLoad)));
			logger.log(Level.INFO, CONFIG_ENABLE_SMALL_BOATS_LOAD + ": " + this.enableSmallBoatsLoad);
		} catch (NumberFormatException e)
		{
			logger.log(Level.WARNING, "Failed to parse one of the configuration values. " + e.getMessage(), e);
		}
	}
	
	@Override
	public void preInit() 
	{
		ModActions.init();
	}

	@Override
	public void init() 
	{
		logger.log(Level.INFO, "Registering stable master template");
		ModCreatures.init();
		stableMasterBuilder = new StableMaster(specifyStableMasterId, stableMasterId);
		ModCreatures.addCreature(stableMasterBuilder);
	}

	@Override
	public void onItemTemplatesCreated() 
	{
		// Create Horse Redemption Token Item Template.
		if (!this.specifyMountTokenId)
		{
			this.mountTokenId = IdFactory.getIdFor(MOUNT_TOKEN_IDENTIFIER, IdType.ITEMTEMPLATE);
		}
		logger.log(Level.INFO, "Creating Mount Token item template with ID: " + 
				this.mountTokenId + ".");

		try
		{
			short [] mountTokenItemTypes = new short[] 
				{ ITEM_TYPE_LEATHER, ITEM_TYPE_MEAT, ITEM_TYPE_NOTAKE, ITEM_TYPE_INDESTRUCTIBLE,
					ITEM_TYPE_NODROP, ITEM_TYPE_FULLPRICE, ITEM_TYPE_HASDATA, ITEM_TYPE_NORENAME,
					ITEM_TYPE_FLOATING, ITEM_TYPE_NOTRADE, ITEM_TYPE_SERVERBOUND, ITEM_TYPE_NAMED,
					ITEM_TYPE_NOBANK, ITEM_TYPE_MISSION, ITEM_TYPE_NODISCARD, 
					ITEM_TYPE_NEVER_SHOW_CREATION_WINDOW_OPTION, ITEM_TYPE_NO_IMPROVE
				};
			ItemTemplateFactory.getInstance().createItemTemplate(
					mountTokenId, MOUNT_TOKEN_SIZE, 
					"mount token", "mount tokens", 
					"excellent", "good", "ok", "poor", 
					"A token to reclaim your mount from the stable master.", 
					mountTokenItemTypes, MOUNT_TOKEN_IMAGE_NUMBER, 
					BehaviourList.itemBehaviour, 0, Long.MAX_VALUE, 
					mountTokenCentimetersX, mountTokenCentimetersY, 
					mountTokenCentimetersZ, (int) MiscConstants.NOID, 
					MiscConstants.EMPTY_BYTE_PRIMITIVE_ARRAY, 
					"model.writ.", MOUNT_TOKEN_DIFFICULTY, 
					mountTokenMinimumWeightGrams, ItemMaterials.MATERIAL_PAPER, 
					MOUNT_TOKEN_VALUE, MOUNT_TOKEN_IS_PURCHASED,
					MOUNT_TOKEN_ARMOR_TYPE);
       
		} catch (IOException ioEx)
		{
			logException("Failed to create Mount Token item template.", ioEx);
            throw new RuntimeException(ioEx);
		}
	}

	@Override
	public void onServerStarted()
	{
		// Get the stable master ID that was ultimately used.
		stableMasterId = stableMasterBuilder.getTemplateId();

		logger.log(Level.INFO, "Registering exchange/redeem/load actions.");
		logger.log(Level.INFO, "mountTokenId = " + mountTokenId);
		logger.log(Level.INFO, "stableMasterId = " + stableMasterId);
		ModActions.registerAction(new ExchangeAction(mountTokenId, stableMasterId, mountTokenMinimumWeightGrams,
				mountTokenMaximumWeightGrams, exchangeMountCostIrons, enableNoNpcExchange));
		ModActions.registerAction(new RedeemAction(mountTokenId));
		ModActions.registerAction(new LoadTokenAction(mountTokenId, enableSmallBoatsLoad));
		ModActions.registerAction(new UnloadTokenAction(mountTokenId));
	}

}
