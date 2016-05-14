package org.tmarchuk.wurmunlimited.server.stablemaster;

/**
 * Created by Tyson Marchuk on 2016-04-27.
 */

// From Wurm Unlimited Server
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.CreatureHelper;
import com.wurmonline.server.creatures.CreaturePos;
import com.wurmonline.server.creatures.CreatureStatus;
import com.wurmonline.server.creatures.Creatures;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.Items;
import com.wurmonline.server.NoSuchItemException;

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

public class RedeemAction implements ModAction, BehaviourProvider, ActionPerformer
{
	private static Logger logger = Logger.getLogger(RedeemAction.class.getName());

	// Constants
	
	// Configuration
	private final int animalTokenId;
	
	// Action data
	private final short actionId;
	private final ActionEntry actionEntry;
	
	public RedeemAction(int animalTokenId) 
	{
		this.animalTokenId = animalTokenId;
		actionId = (short) ModActions.getNextActionId();
		actionEntry = ActionEntry.createEntry(actionId, "Redeem animal token", "redeeming", new int[] { 0 /* ACTION_TYPE_QUICK */, 48 /* ACTION_TYPE_ENEMY_ALWAYS */});
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
	public List<ActionEntry> getBehavioursFor(Creature performer, Item target) 
	{
		// TODO: Probably need a bunch more checks to make sure it's not redeemed while in a boat or swimming or something for example.
		try
		{
			if ((performer instanceof Player) && 
					((target.getTemplateId() == animalTokenId)) && (target.getParent().isInventory())
					&& !target.isTraded()) 
			{
				return Arrays.asList(actionEntry);
			} 
			else 
			{
				return null;
			}
		} catch (NoSuchItemException e)
		{
			logger.log(Level.WARNING, e.getMessage(), e);
			return null;
		}
	}

	@Override
	public List<ActionEntry> getBehavioursFor(Creature performer, Item subject, Item target) 
	{
		return getBehavioursFor(performer, target);
	}

	@Override
	public short getActionId() {
		return actionId;
	}

	@Override
	public boolean action(Action action, Creature performer, Item source, Item target, short num, float counter) 
	{
		return action(action, performer, target, num, counter);
	}

	@Override
	public boolean action(Action action, Creature performer, Item target, short num, float counter) 
	{
		try 
		{
			if (target.isTraded())
			{
				performer.getCommunicator().sendNormalServerMessage("You cannot redeem an animal token while it is part of a trade.");
				return true;
			}

			Creature theAnimal = Creatures.getInstance().getCreature(target.getData());

			// Set the location to the current player location.
			CreaturePos performerPos = performer.getStatus().getPosition();
			CreatureStatus animalStatus = theAnimal.getStatus();
			animalStatus.setPositionXYZ(performerPos.getPosX(), performerPos.getPosY(), 
					performerPos.getPosZ());
			animalStatus.getPosition().setZoneId(performerPos.getZoneId());
			
			// Set the kingdom of the animal to the player's kingdom.
			theAnimal.setKingdomId(performer.getKingdomId());
			
			// Restore animal to world.
			CreatureHelper.showCreature(theAnimal);

			// Delete redemption token from player's inventory.
			Items.destroyItem(target.getWurmId());

			// Inform the player.
			performer.getCommunicator().sendNormalServerMessage("You redeem your animal token for an animal!" );
			return true;
		} catch (Exception e) {
			logger.log(Level.WARNING, e.getMessage(), e);
			return true;

		}
	}
}
