package org.tmarchuk.wurmunlimited.server.stablemaster;

/**
 * Created by Tyson Marchuk on 2016-04-27.
 */

// From Wurm Unlimited Server
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.CreaturePos;
import com.wurmonline.server.creatures.CreatureStatus;
import com.wurmonline.server.creatures.Creatures;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.Items;

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
	private final int mountTokenId;
	
	// Action data
	private final short actionId;
	private final ActionEntry actionEntry;
	
	public RedeemAction(int mountTokenId) 
	{
		this.mountTokenId = mountTokenId;
		actionId = (short) ModActions.getNextActionId();
		actionEntry = ActionEntry.createEntry(actionId, "Redeem mount token", "redeeming", new int[] { 0 /* ACTION_TYPE_QUICK */, 48 /* ACTION_TYPE_ENEMY_ALWAYS */});
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
		if ((performer instanceof Player) && 
				((target.getTemplateId() == mountTokenId))) 
		{
			return Arrays.asList(actionEntry);
		} 
		else 
		{
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
			Creature theMount = Creatures.getInstance().getCreature(target.getData());

			// Set the location to the current player location.
			CreaturePos performerPos = performer.getStatus().getPosition();
			CreatureStatus mountStatus = theMount.getStatus();
			mountStatus.setPositionXYZ(performerPos.getPosX(), performerPos.getPosY(), 
					performerPos.getPosZ());
			mountStatus.getPosition().setZoneId(performerPos.getZoneId());
			
			// Restore mount to world.
			CreatureHelper.showCreature(theMount);

			// Delete redemption token from player's inventory.
			Items.destroyItem(target.getWurmId());

			// Inform the player.
			performer.getCommunicator().sendNormalServerMessage("You redeem your mount token for a mount!" );
			return true;
		} catch (Exception e) {
			logger.log(Level.WARNING, e.getMessage(), e);
			return true;

		}
	}
}
