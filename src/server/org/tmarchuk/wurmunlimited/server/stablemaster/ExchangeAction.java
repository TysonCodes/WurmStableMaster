package org.tmarchuk.wurmunlimited.server.stablemaster;

/**
 * Created by Tyson Marchuk on 2016-04-27.
 */

// From Wurm Unlimited Server
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.behaviours.Vehicle;
import com.wurmonline.server.behaviours.Vehicles;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.Creatures;
import com.wurmonline.server.creatures.NoSuchCreatureException;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemFactory;
import com.wurmonline.server.players.Player;

// From Ago's modloader
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

// Base Java
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExchangeAction implements ModAction, BehaviourProvider, ActionPerformer
{
	private static Logger logger = Logger.getLogger(ExchangeAction.class.getName());
	
	// Constants
	private static final int HORSE_TEMPLATE_ID = 64;
	private static final float HORSE_REDEMPTION_TOKEN_QUALITY = 100.0f;
	
	// Configuration
	private final int horseRedemptionTokenId;
	private final int stableMasterId;
	
	// Action data
	private final short actionId;
	private final ActionEntry actionEntry;

	// Creature handling
	private CreatureHelper cHelper = new CreatureHelper();

	public ExchangeAction(int horseRedemptionTokenId, int stableMasterId) 
	{
		this.horseRedemptionTokenId = horseRedemptionTokenId;
		this.stableMasterId = stableMasterId;
		actionId = (short) ModActions.getNextActionId();
		actionEntry = ActionEntry.createEntry(actionId, "Exchange mount", "exchanging", new int[] { 0 /* ACTION_TYPE_QUICK */, 48 /* ACTION_TYPE_ENEMY_ALWAYS */, 37 /* ACTION_TYPE_NEVER_USE_ACTIVE_ITEM */});
		ModActions.registerAction(actionEntry);
	}

	@Override
	public BehaviourProvider getBehaviourProvider() {
		return this;
	}

	@Override
	public ActionPerformer getActionPerformer() {
		return this;
	}

	@Override
	public List<ActionEntry> getBehavioursFor(Creature performer, Item subject, Creature target) 
	{
		return getBehavioursFor(performer, target);
	}

	@Override
	public List<ActionEntry> getBehavioursFor(Creature performer, Creature target) 
	{
		if ((performer instanceof Player) && 
				((target.getTemplate().getTemplateId() == HORSE_TEMPLATE_ID) || 
				 target.getTemplate().isHellHorse())) 
		{
			return Arrays.asList(actionEntry);
		} 
		if ((performer instanceof Player) &&
			(target.getTemplate().getTemplateId() == stableMasterId) && 
			performer.isVehicleCommander())
		{
			Vehicle pVehicle = Vehicles.getVehicleForId(performer.getVehicle());
			if (pVehicle.isCreature())
			{
				return Arrays.asList(actionEntry);
			}
			else 
			{
				return null;
			}
		}
		else 
		{
			return null;
		}
	}

	@Override
	public short getActionId() {
		return actionId;
	}

	@Override
	public boolean action(Action action, Creature performer, Creature target, short num, float counter) 
	{
		// Make sure we're not at max number items.
        if (!performer.getInventory().mayCreatureInsertItem()) 
        {
            performer.getCommunicator().sendNormalServerMessage("You do not have enough room in your inventory.");
            return true;
        }

        // Get the creature we're trying to exchange. For now if using the Stable Master this needs to be
        // ridden. If this is a mount and we're configured to allow NPC-less control then let it go ahead.
        Creature mount = null;
		if ((target.getTemplate().getTemplateId() == stableMasterId) && performer.isVehicleCommander())
		{
			Vehicle pVehicle = Vehicles.getVehicleForId(performer.getVehicle());
			if (pVehicle.isCreature())
			{
				try
				{
					mount = Creatures.getInstance().getCreature(performer.getVehicle());
				} catch (NoSuchCreatureException e)
				{
					logger.log(Level.WARNING, "Attempted to get mount Creature object and failed. " + e.getMessage(), e);
				}
                
			}
		}

		// TODO: Add checks for more mount types and configuration to turn off for servers that want to
		// require an NPC.
		if (target.isHorse())
        {
			mount = target;
        }
		
		if (mount == null)
		{
        	performer.getCommunicator().sendNormalServerMessage("Nothing to exchange!");
			return true;
		}
        
        try 
		{
			// Create new redemption token from horse.
			Item redemptionToken = ItemFactory.createItem(horseRedemptionTokenId, 
					HORSE_REDEMPTION_TOKEN_QUALITY, performer.getName());
			redemptionToken.setDescription(getHorseDescription(mount));
			redemptionToken.setName(getHorseName(mount));
			redemptionToken.setData(mount.getWurmId());
			redemptionToken.setWeight((int)Math.min(redemptionToken.getTemplate().getWeightGrams(), mount.getStatus().getBody().getWeight(mount.getStatus().fat)), false);
			redemptionToken.setLastOwnerId(performer.getWurmId());
			redemptionToken.setFemale(mount.getSex() == 1);

			// Add token to player's inventory.
			performer.getInventory().insertItem(redemptionToken, true);
			
			// Remove horse from world.
			cHelper.hideCreature(mount);

			// Let the player know.
			performer.getCommunicator().sendNormalServerMessage("You exchange your horse with the stable master for a redemption token." );
			return true;
		} catch (Exception e) {
			logger.log(Level.WARNING, e.getMessage(), e);
			return true;

		}
	}

	@Override
	public boolean action(Action action, Creature performer, Item source, Creature target, short num, float counter) 
	{
		return action(action, performer, target, num, counter);
	}

	private String getHorseDescription(Creature target)
	{
		String toReturn = target.getStatus().getAgeString().toLowerCase() + " ";
        if(target.getTemplate().getTemplateId() == HORSE_TEMPLATE_ID) 
        {
            toReturn += "grey";
            if(target.hasTrait(15)) 
            {
            	toReturn += "brown";
            } else if(target.hasTrait(16)) 
            {
            	toReturn += "gold";
            } else if(target.hasTrait(17)) 
            {
            	toReturn += "black";
            } else if(target.hasTrait(18)) 
            {
            	toReturn += "white";
            } else if(target.hasTrait(24)) 
            {
            	toReturn += "piebaldPinto";
            } else if(target.hasTrait(25)) 
            {
            	toReturn += "bloodBay";
            } else if(target.hasTrait(23)) 
            {
            	toReturn += "ebonyBlack";
            }
        }
        toReturn += " " + target.getNameWithoutPrefixes().toLowerCase();
        return toReturn;
	}

	private String getHorseName(Creature target)
	{
		String toReturn = "horse redemption token";
        return toReturn;
	}
}
