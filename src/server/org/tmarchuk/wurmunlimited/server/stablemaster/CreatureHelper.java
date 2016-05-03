package org.tmarchuk.wurmunlimited.server.stablemaster;

/**
 * Created by Tyson Marchuk on 2016-04-29.
 */

// From Wurm Unlimited Server
import com.wurmonline.server.MiscConstants;
import com.wurmonline.server.Players;
import com.wurmonline.server.behaviours.ActionStack;
import com.wurmonline.server.behaviours.MethodsItems;
import com.wurmonline.server.behaviours.Vehicles;
import com.wurmonline.server.creatures.CombatHandler;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.CreatureStatus;
import com.wurmonline.server.creatures.Creatures;

//From Ago's modloader
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

//Base java
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import java.util.logging.Level;

public class CreatureHelper
{
	private static Logger logger = Logger.getLogger(CreatureHelper.class.getName());

	// Constants
	private static final int SEA_SERPENT_TEMPLATE_ID = 70;
	
	// CreatureStatus private methods/fields.
	private static Method setDead;
	private static Field modtype;
	
	// CombatHandler private methods.
	private static Method clearMoveStack;

	// Creature private fields.
	private static Field actions;
	private static Field damageCounter;

	// Creatures private methods/fields.
	private static Method addCreature;
	private static Field numberOfNice;
	private static Field numberOfAgg;
	private static Field numberOfTyped;
	private static Field kingdomCreatures;
	private static Field seaMonsters;
	private static Field seaHunters;
	
	
	static {
		try {
			// CreatureStatus
			setDead = ReflectionUtil.getMethod(CreatureStatus.class, "setDead");
			modtype = ReflectionUtil.getField(CreatureStatus.class, "modtype");
			
			// CombatHandler
			clearMoveStack = ReflectionUtil.getMethod(CombatHandler.class, "clearMoveStack");
			
			// Creature
			actions = ReflectionUtil.getField(Creature.class, "actions");
			damageCounter = ReflectionUtil.getField(Creature.class, "damageCounter");
			
			// Creatures
			addCreature = Creatures.class.getDeclaredMethod("addCreature", new Class[] {Creature.class, boolean.class});
			numberOfNice = ReflectionUtil.getField(Creatures.class, "numberOfNice"); 
			numberOfAgg = ReflectionUtil.getField(Creatures.class, "numberOfAgg");
			numberOfTyped = ReflectionUtil.getField(Creatures.class, "numberOfTyped");
			kingdomCreatures = ReflectionUtil.getField(Creatures.class, "kingdomCreatures");
			seaMonsters = ReflectionUtil.getField(Creatures.class, "seaMonsters");
			seaHunters = ReflectionUtil.getField(Creatures.class, "seaHunters");
		} catch (NoSuchMethodException | NoSuchFieldException e) 
		{
			logger.log(Level.SEVERE, "Failed to get methods/fields. " + e.getMessage(), e);
			throw new RuntimeException(e);
		}
	}

	public CreatureHelper()
	{
	}
	
	// Really this is only meant to work on horses for now. A lot of other things that
	// Creature.die() does have been removed because they don't apply to a horse but
	// they may apply to a player or another creature. Use with caution...
	public static void hideCreature(Creature creat)
	{
		Creatures allCreatures = Creatures.getInstance();
		CreatureStatus cStatus = creat.getStatus();
		
		// Reset a bunch of things the creature might be doing.
		clearMoveStack(creat.getCombatHandler());
		creat.combatRound = 0;
		
		// Stop dragging (so pulling a cart or wagon, etc.)
		if (creat.getDraggedItem() != null)
		{
			MethodsItems.stopDragging(creat, creat.getDraggedItem());
		}

		// Make sure no other creatures or players are targeting or attacking this one anymore.
		allCreatures.setCreatureDead(creat);
		Players.getInstance().setCreatureDead(creat);
		
		// Not really sure. I think this removes the creature as a target or opponent of guards.
		// Harmless if it does nothing I hope.
		if (creat.currentVillage != null)
		{
			creat.currentVillage.removeTarget(creat.getWurmId(), true);
		}

		// Stop any actions the creature is taking and clear the queue.
		try
		{
			creat.stopCurrentAction();
	        
			ActionStack cActions = (ActionStack) ReflectionUtil.getPrivateField(creat, actions);
			cActions.clear();

		} catch (IllegalAccessException | IllegalArgumentException | ClassCastException e) 
		{
            logger.log(Level.WARNING, creat.getName() + ":" + e.getMessage(), e);
        }
        
		// Get off any bridges.
		creat.setBridgeId(MiscConstants.NOID);

		// Remove from the tile set.
		creat.getCurrentTile().deleteCreature(creat);
		
		// Move to unknown zone.
		try
		{
			creat.savePosition((int) MiscConstants.NOID);
		} catch (IOException e)
		{
			logger.log(Level.WARNING, creat.getName() + ":" + e.getMessage(), e);
		}
		
		// Not sure we should do this... reset damage counter.
		try
		{
			ReflectionUtil.setPrivateField(creat, damageCounter, (short) 0);
		} catch (IllegalArgumentException | IllegalAccessException | ClassCastException e)
		{
			logger.log(Level.WARNING, creat.getName() + ":" + e.getMessage(), e);
		}

		// Update the database and various other things to say the creature is dead.
		setDead(cStatus, true);

		// Make sure nobody is leading the creature anymore. This also cleans up a bunch of 
		// other things calling creat.clearOrders which clears decisions, sets path to 
		// null, sets moving to false, sets target to (-10L, true) removes follower 
		// and sets leader to null
		creat.setLeader((Creature) null); 
		
		// Clean up the creature's view of the world (as it's not in it anymore.)
		creat.destroyVisionArea();

		// Get rid of the rider of the creature if any.
		if (creat.isVehicle())
		{
			Vehicles.getVehicle(creat).kickAll();
		}
		
		// Remove the creature from the various lists that track numbers of creatures
		// This is necessary because when we add it back into the server the easiest 
		// path adds the creature back to all of these lists.
		try
		{
			if (!creat.isFloating())
			{
				if (!creat.isMonster() && !creat.isAggHuman())
				{
					subtractOneFromInt(allCreatures, numberOfNice);
				}
				else
				{
					subtractOneFromInt(allCreatures, numberOfAgg);
				}
			}
			
			if ((byte) ReflectionUtil.getPrivateField(cStatus, modtype) > 0)
			{
				subtractOneFromInt(allCreatures, numberOfTyped);
			}

			if (creat.isAggWhitie() || creat.isDefendKingdom())
			{
				subtractOneFromInt(allCreatures, kingdomCreatures);
			}
			
			if (creat.isFloating())
			{
				if (creat.getTemplate().getTemplateId() == SEA_SERPENT_TEMPLATE_ID)
				{
					subtractOneFromInt(allCreatures, seaMonsters);
				}
				else
				{
					subtractOneFromInt(allCreatures, seaHunters);
				}
			}
		} catch (IllegalArgumentException | IllegalAccessException | ClassCastException e)
		{
			logger.log(Level.WARNING, creat.getName() + ":" + e.getMessage(), e);
		}

		// No longer stunned and no longer has attackers.
		cStatus.setStunned(0.0f);
		creat.trimAttackers(true);
	}
	
	// Really this is only meant to work on horses for now. A lot of other things that
	// Creature.die() does weren't done in hideCreature() so they might be in a weird 
	// state. I doubt this will work for players. Use with caution...
	public static void showCreature(Creature creat)
	{
		Creatures allCreatures = Creatures.getInstance();
		
		// Update the database and various other things to say the creature is not dead.
		setDead(creat.getStatus(), false);
		
		// Remove and then re-add the creature. Easier than duplicating all the code in
		// addCreatures(), part of which is called even for dead creatures.
		allCreatures.removeCreature(creat);
		callPrivateMethod(allCreatures, addCreature, creat, false);
		
		// Recreate the vision area for the creature we destroyed when hiding it.
		try
		{
			if (creat.getVisionArea() == null)
			{
				creat.createVisionArea();
			}
		} catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	// Helpers to work with private methods/fields.
	// ============================================
	private static <T> T callPrivateMethod(Object obj, Method method, Object... args) 
	{
		try 
		{
			return ReflectionUtil.callPrivateMethod(obj, method, args);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) 
		{
			throw new RuntimeException(e);
		}
	}
	
	private static void subtractOneFromInt(Object obj, Field field)
	{
		try
		{
			int tempValue = 0;
			tempValue = ReflectionUtil.getPrivateField(obj, field);
			ReflectionUtil.setPrivateField(obj, field, tempValue - 1);
		} catch (IllegalArgumentException | IllegalAccessException | ClassCastException e)
		{
			logger.log(Level.WARNING, ((Creature) obj).getName() + ":" + e.getMessage(), e);
		}
	}

	// Helpers to access protected and private methods.
	// ================================================
	
	// CreatureStatus
	// --------------
	private static void setDead(CreatureStatus cStatus, boolean isDead)
	{
		callPrivateMethod(cStatus, setDead, isDead);
	}
	
	// CombatHandler
	// -------------
	private static void clearMoveStack(CombatHandler cHandler)
	{
		callPrivateMethod(cHandler, clearMoveStack);
	}
}
