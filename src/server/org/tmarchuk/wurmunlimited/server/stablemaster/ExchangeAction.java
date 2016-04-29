package org.tmarchuk.wurmunlimited.server.stablemaster;

/**
 * Created by Tyson Marchuk on 2016-04-27.
 */

// From Wurm Unlimited Server
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
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
	
	// Action data
	private final short actionId;
	private final ActionEntry actionEntry;

	public ExchangeAction(int horseRedemptionTokenId) 
	{
		this.horseRedemptionTokenId = horseRedemptionTokenId;
		actionId = (short) ModActions.getNextActionId();
		actionEntry = ActionEntry.createEntry(actionId, "Exchange Mount", "exchanging", new int[] { 0 /* ACTION_TYPE_QUICK */, 48 /* ACTION_TYPE_ENEMY_ALWAYS */, 37 /* ACTION_TYPE_NEVER_USE_ACTIVE_ITEM */});
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

        try 
		{
			// Create new redemption token from horse.
			Item redemptionToken = ItemFactory.createItem(horseRedemptionTokenId, 
					HORSE_REDEMPTION_TOKEN_QUALITY, performer.getName());
			redemptionToken.setDescription(getHorseDescription(target));
			redemptionToken.setName(getHorseName(target));
			redemptionToken.setData(target.getWurmId());
			redemptionToken.setWeight((int)Math.min(redemptionToken.getTemplate().getWeightGrams(), target.getStatus().getBody().getWeight(target.getStatus().fat)), false);
			redemptionToken.setLastOwnerId(performer.getWurmId());
			redemptionToken.setFemale(target.getSex() == 1);

			// Add token to player's inventory.
			performer.getInventory().insertItem(redemptionToken, true);
			
			// TODO: Remove horse from world.

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
