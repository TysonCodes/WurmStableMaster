package org.tmarchuk.wurmunlimited.server.stablemaster;

/**
 * Created by Tyson Marchuk on 2016-05-02.
 */

//From Wurm Unlimited Server
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.NoSuchPlayerException;
import com.wurmonline.server.behaviours.Action;
import com.wurmonline.server.behaviours.ActionEntry;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.NoSuchCreatureException;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.players.Player;

// Base Java
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

// From Ago's modloader
import org.gotti.wurmunlimited.modsupport.actions.ActionPerformer;
import org.gotti.wurmunlimited.modsupport.actions.BehaviourProvider;
import org.gotti.wurmunlimited.modsupport.actions.ModAction;
import org.gotti.wurmunlimited.modsupport.actions.ModActions;

public class LoadTokenAction implements ModAction, BehaviourProvider, ActionPerformer
{
	private static Logger logger = Logger.getLogger(LoadTokenAction.class.getName());

	// Configuration
	private final int mountTokenId;
	private static final String actionString = "Load mount token";
	private static final String actionVerb = "loading";
	private static final int[] actionTypes = new int [] 
			{
					36,		// ACTION_TYPE_ALWAYS_USE_ACTIVE_ITEM
					48		// ACTION_TYPE_ENEMY_ALWAYS
			};
	
	// Action data
	private final short actionId;
	private final ActionEntry actionEntry;
	

	public LoadTokenAction(int mountTokenId) 
	{
		this.mountTokenId = mountTokenId;
		actionId = (short) ModActions.getNextActionId();
		actionEntry = ActionEntry.createEntry(actionId, actionString, actionVerb, actionTypes);
		ModActions.registerAction(actionEntry);
	}

	@Override
	public BehaviourProvider getBehaviourProvider() 
	{
		return this;
	}

	@Override
	public ActionPerformer getActionPerformer() 
	{
		return this;
	}

	@Override
	public List<ActionEntry> getBehavioursFor(Creature performer, Item subject, Item target) 
	{
		if ((performer instanceof Player) && 
				(subject.getTemplateId() == mountTokenId) &&
				((target.isBoat()))) 
		{
			return Arrays.asList(actionEntry);
		} 
		else 
		{
			return null;
		}
	}

	@Override
	public short getActionId()
	{
		return actionId;
	}

	@Override
	public boolean action(Action action, Creature performer, Item source, Item target, short num, float counter) 
	{
		// TODO: Make sure there is space on the boat.
		// TODO: Make sure performer has permission to open the inventory of the boat.

		// Make sure source item is a token 
		if (source.getTemplateId() != mountTokenId)
		{
			performer.getCommunicator().sendNormalServerMessage("You must activate your mount token to load it on the boat.");
			return true;
		}
		
		// Make sure target item is a boat.
		if (!target.isBoat())
		{
			performer.getCommunicator().sendNormalServerMessage("Mount tokens can only be loaded on boats.");
			return true;
		}
		
		// TODO: Check if the boat type is allowed.
		
		// Move the item from the performer's inventory to the boat's inventory.
		try
		{
			boolean result = source.moveToItem(performer, target.getWurmId(), true);
			if (result)
			{
				performer.getCommunicator().sendNormalServerMessage("You load your mount token onto the boat.");
			}
			else
			{
				performer.getCommunicator().sendNormalServerMessage("You fail to load your mount token onto the boat.");
			}
			return true;
		} catch (NoSuchItemException | NoSuchPlayerException | NoSuchCreatureException e)
		{
			logger.log(Level.WARNING, e.getMessage(), e);
			return true;
		}
	}
}
