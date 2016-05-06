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
import com.wurmonline.server.questions.ExchangeAnimalQuestion;

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
	private static final float ANIMAL_TOKEN_QUALITY = 100.0f;
	
	// Configuration
	private static int animalTokenId;
	private final int stableMasterId;
	private static int animalTokenMinimumWeightGrams;
	private static int animalTokenMaximumWeightGrams;
	private final int exchangeAnimalCostIrons;
	private final boolean enableNoNpcExchange;
	
	// Action data
	private final short actionId;
	private final ActionEntry actionEntry;

	public ExchangeAction(int animalTokenId, int stableMasterId, int animalTokenMinimumWeightGrams, 
			int animalTokenMaximumWeightGrams, int exchangeAnimalCostIrons, boolean enableNoNpcExchange) 
	{
		ExchangeAction.animalTokenId = animalTokenId;
		this.stableMasterId = stableMasterId;
		ExchangeAction.animalTokenMinimumWeightGrams = animalTokenMinimumWeightGrams;
		ExchangeAction.animalTokenMaximumWeightGrams = animalTokenMaximumWeightGrams;
		this.exchangeAnimalCostIrons = exchangeAnimalCostIrons;
		this.enableNoNpcExchange = enableNoNpcExchange;
		actionId = (short) ModActions.getNextActionId();
		actionEntry = ActionEntry.createEntry(actionId, "Exchange animal", "exchanging", new int[] { 0 /* ACTION_TYPE_QUICK */, 48 /* ACTION_TYPE_ENEMY_ALWAYS */, 37 /* ACTION_TYPE_NEVER_USE_ACTIVE_ITEM */});
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
		// If we are configured to allow direct exchanges for no cost check if this is an animal.
		// TODO: Update this to be any animal and not just horses and hell horses.
		if (this.enableNoNpcExchange && (performer instanceof Player) && 
				((target.getTemplate().getTemplateId() == HORSE_TEMPLATE_ID) || 
				 target.getTemplate().isHellHorse())) 
		{
			return Arrays.asList(actionEntry);
		} 
		
		// Check if we're using a stable master.
		if ((performer instanceof Player) &&
			(target.getTemplate().getTemplateId() == stableMasterId) && 
			performer.isVehicleCommander())
		{
			Vehicle pVehicle = Vehicles.getVehicleForId(performer.getVehicle());
			if (pVehicle.isCreature())
			{
				return Arrays.asList(actionEntry);
			}
		}
		
		// Doesn't apply.
		return null;
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
        
        // Check max weight of player
        if (!performer.canCarry(getAnimalTokenWeight(target)))
        {
            performer.getCommunicator().sendNormalServerMessage("You would not be able to carry the animal token. You need to drop some things first.");
            return true;
        }

        // Get the creature we're trying to exchange. For now if using the Stable Master this needs to be
        // ridden. If this is an animal and we're configured to allow NPC-less control then let it go ahead.
        Creature animal = null;
		if ((target.getTemplate().getTemplateId() == stableMasterId) && performer.isVehicleCommander())
		{
			Vehicle pVehicle = Vehicles.getVehicleForId(performer.getVehicle());
			if (pVehicle.isCreature())
			{
				try
				{
					animal = Creatures.getInstance().getCreature(performer.getVehicle());
					
					// Ask user to pay.
					final ExchangeAnimalQuestion eQuestion = new ExchangeAnimalQuestion(performer, new Creature[] {animal}, exchangeAnimalCostIrons);
					eQuestion.sendQuestion();
					return true;
				} catch (NoSuchCreatureException e)
				{
					logger.log(Level.WARNING, "Attempted to get animal Creature object and failed. " + e.getMessage(), e);
				}
                
			}
		}

		// If we're configured to allow direct exchanges for no cost check if this is an animal.
		// TODO: Add checks for more animal types.
		if (this.enableNoNpcExchange && target.isHorse())
        {
			animal = target;
        }
		
		if (animal == null)
		{
        	performer.getCommunicator().sendNormalServerMessage("Nothing to exchange!");
			return true;
		}
        
		// Do the exchange
		exchangeAnimalForToken(performer, animal);
		return true;
	}

	@Override
	public boolean action(Action action, Creature performer, Item source, Creature target, short num, float counter) 
	{
		return action(action, performer, target, num, counter);
	}

	public static void exchangeAnimalForToken(final Creature performer, final Creature animal)
	{
        try 
		{
			// Create new animal token from animal.
			Item animalToken = ItemFactory.createItem(ExchangeAction.animalTokenId, 
					ANIMAL_TOKEN_QUALITY, performer.getName());
			animalToken.setDescription(getAnimalDescription(animal));
			animalToken.setName(getAnimalName(animal));
			animalToken.setData(animal.getWurmId());
			animalToken.setWeight(getAnimalTokenWeight(animal), false);
			animalToken.setLastOwnerId(performer.getWurmId());
			animalToken.setFemale(animal.getSex() == 1);

			// Add token to player's inventory.
			performer.getInventory().insertItem(animalToken, true);
			
			// Remove animal from world.
			CreatureHelper.hideCreature(animal);

			// Let the player know.
			performer.getCommunicator().sendNormalServerMessage("You exchange your animal with the stable master for an animal token." );
		} catch (Exception e) {
			logger.log(Level.WARNING, e.getMessage(), e);
		}
	}
	
	private static int getAnimalTokenWeight(Creature animal)
	{
		int calculatedAnimalWeight = (int) animal.getStatus().getBody().getWeight(animal.getStatus().fat);
		int animalTokenWeight = Math.min(animalTokenMaximumWeightGrams, 
				Math.max(animalTokenMinimumWeightGrams, calculatedAnimalWeight));
		return animalTokenWeight;
	}
	
	private static String getAnimalDescription(Creature target)
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

	private static String getAnimalName(Creature target)
	{
		String toReturn = "animal token";
        return toReturn;
	}
}
