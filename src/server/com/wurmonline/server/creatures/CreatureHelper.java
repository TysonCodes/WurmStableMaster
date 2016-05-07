package com.wurmonline.server.creatures;


import com.wurmonline.server.Items;

/**
 * Created by Tyson Marchuk on 2016-04-29.
 */

// From Wurm Unlimited Server
import com.wurmonline.server.MiscConstants;
import com.wurmonline.server.NoSuchItemException;
import com.wurmonline.server.Players;
import com.wurmonline.server.behaviours.MethodsItems;
import com.wurmonline.server.behaviours.Seat;
import com.wurmonline.server.behaviours.Vehicle;
import com.wurmonline.server.behaviours.Vehicles;
import com.wurmonline.server.bodys.BodyFactory;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.CreatureStatus;
import com.wurmonline.server.creatures.Creatures;
import com.wurmonline.server.creatures.Offspring;
import com.wurmonline.server.intra.PlayerTransfer;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemMetaData;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.structures.NoSuchStructureException;
import com.wurmonline.server.structures.Structure;
import com.wurmonline.server.structures.Structures;

//From Ago's modloader
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

//Base java
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.logging.Logger;
import java.util.Set;
import java.util.logging.Level;

public class CreatureHelper
{
	private static Logger logger = Logger.getLogger(CreatureHelper.class.getName());

	// Constants
	private static final int SEA_SERPENT_TEMPLATE_ID = 70;
	
	// CreatureStatus private methods/fields.
	private static Field modtype;
	
	// Creatures private methods/fields.
	private static Field numberOfNice;
	private static Field numberOfAgg;
	private static Field numberOfTyped;
	private static Field kingdomCreatures;
	private static Field seaMonsters;
	private static Field seaHunters;
	
	
	static {
		try {
			// CreatureStatus
			modtype = ReflectionUtil.getField(CreatureStatus.class, "modtype");
			
			// Creatures
			numberOfNice = ReflectionUtil.getField(Creatures.class, "numberOfNice"); 
			numberOfAgg = ReflectionUtil.getField(Creatures.class, "numberOfAgg");
			numberOfTyped = ReflectionUtil.getField(Creatures.class, "numberOfTyped");
			kingdomCreatures = ReflectionUtil.getField(Creatures.class, "kingdomCreatures");
			seaMonsters = ReflectionUtil.getField(Creatures.class, "seaMonsters");
			seaHunters = ReflectionUtil.getField(Creatures.class, "seaHunters");
		} catch (NoSuchFieldException e) 
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
		creat.getCombatHandler().clearMoveStack();
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
	        creat.actions.clear();

		} catch (IllegalArgumentException | ClassCastException e) 
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
		creat.damageCounter = 0;

		// Update the database and various other things to say the creature is dead.
		try
		{
			cStatus.setDead(true);
		} catch (IOException e)
		{
			logger.log(Level.WARNING, creat.getName() + " failed to call setDead on status:" + e.getMessage(), e);
		}

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
		try
		{
			creat.getStatus().setDead(false);
		} catch (IOException e)
		{
			logger.log(Level.WARNING, creat.getName() + " failed to call setDead on status:" + e.getMessage(), e);
		}
		
		// Remove and then re-add the creature. Easier than duplicating all the code in
		// addCreatures(), part of which is called even for dead creatures.
		allCreatures.removeCreature(creat);
		allCreatures.addCreature(creat, false);
		
		// Recreate the vision area for the creature we destroyed when hiding it.
		try
		{
			if (creat.getVisionArea() == null)
			{
				creat.createVisionArea();
			}
		} catch (Exception e)
		{
			logger.log(Level.WARNING, creat.getName() + ":" + e.getMessage(), e);
		}
	}
	
	// Send the minimum data associated with a creature to 'outputStream'.
	public static void toStream(Creature animal, DataOutputStream outputStream) throws IOException
	{
		// Send Offspring data.
		Offspring baby = animal.getOffspring();
		if (baby != null)
		{
			outputStream.writeBoolean(true);
			outputStream.writeLong(getPrivateLong(baby, "mother"));
			outputStream.writeLong(getPrivateLong(baby, "father"));
			outputStream.writeLong(getPrivateLong(baby, "traits"));
			outputStream.writeByte((byte) baby.getDaysLeft());
		}
		else
		{
			outputStream.writeBoolean(false);
		}
		
		// Save creature data to output stream.
		outputStream.writeLong(animal.getWurmId());
		outputStream.writeUTF(animal.getName());
		outputStream.writeUTF(animal.getTemplate().getName());
		outputStream.writeByte(animal.getSex());
		outputStream.writeShort(animal.getCentimetersHigh());
		outputStream.writeShort(animal.getCentimetersLong());
		outputStream.writeShort(animal.getCentimetersWide());
		outputStream.writeLong(animal.getInventory().getWurmId());
		outputStream.writeLong(animal.getBody().getId());
		outputStream.writeLong(animal.getBuildingId());
		outputStream.writeShort(animal.getStatus().getStamina() & 0xFFFF);
		outputStream.writeShort(animal.getStatus().getHunger() & 0xFFFF);
		outputStream.writeFloat(animal.getStatus().getNutritionlevel());
		outputStream.writeShort(animal.getStatus().getThirst() & 0xFFFF);
		outputStream.writeBoolean(animal.isDead());
		outputStream.writeBoolean(animal.isStealth());
		outputStream.writeByte(animal.getCurrentKingdom());
		outputStream.writeInt(animal.getStatus().age);
		outputStream.writeLong(animal.getStatus().lastPolledAge);
		outputStream.writeByte(animal.getStatus().fat);
		outputStream.writeLong(animal.getStatus().traits);
		outputStream.writeLong(animal.dominator);
		outputStream.writeLong(animal.getMother());
		outputStream.writeLong(animal.getFather());
		outputStream.writeBoolean(animal.isReborn());
		outputStream.writeFloat(animal.getLoyalty());
		outputStream.writeLong(animal.getStatus().lastPolledLoyalty);
		outputStream.writeBoolean(animal.isOffline());
		outputStream.writeBoolean(animal.isStayonline());
		outputStream.writeShort(animal.getStatus().detectInvisCounter);
		outputStream.writeByte(animal.getDisease());
		outputStream.writeLong(animal.getLastGroomed());
		outputStream.writeLong(animal.getVehicle());
		outputStream.writeByte(animal.getStatus().modtype);
		outputStream.writeUTF(animal.petName);

        // Send all non-temporary skills.
        Skill[] animalSkills = animal.getSkills().getSkills();
        int numSkills = animalSkills.length;
        for (Skill curSkill : animalSkills)
        {
        	if (curSkill.isTemporary())
        	{
        		numSkills--;
        	}
        }

		// Write the values to the output stream.
		outputStream.writeInt(numSkills);
		for (Skill curSkill : animalSkills)
		{
			if (!curSkill.isTemporary())
			{
				outputStream.writeInt(curSkill.getNumber());
				outputStream.writeDouble(curSkill.getKnowledge());
				outputStream.writeDouble(curSkill.getMinimumValue());
				outputStream.writeLong(curSkill.lastUsed);
			}
		}
		
		// Send all items.
		Item[] animalItems = animal.getAllItems();
		int numItems = 0;
		for (Item curItem : animalItems)
		{
			if (!curItem.isBodyPart())
			{
				numItems++;
			}
		}
		
		// Write the values to the output stream.
		outputStream.writeInt(numItems);
		for (Item curItem : animalItems)
		{
			if (!curItem.isBodyPart())
			{
				PlayerTransfer.sendItem(curItem, outputStream, false);
			}
		}
    }

	public static void fromStream(DataInputStream inputStream, float posx, float posy, float posz,
			Set<ItemMetaData> createdItems, boolean frozen) throws IOException
	{
		// Get Offspring and add if necessary.
		boolean hasBaby = inputStream.readBoolean();
		if (hasBaby)
		{
			long mother = inputStream.readLong();
			long father = inputStream.readLong();
			long traits = inputStream.readLong();
			byte daysLeft = inputStream.readByte();
			new Offspring(mother, father, traits, daysLeft, false);
		}
		
		// TODO: Get creature data and create creature.
		long creatureId = inputStream.readLong();
		try
		{
			Creature animal = new Creature(creatureId);
			animal.setName(inputStream.readUTF());
			animal.getStatus().template = CreatureTemplateFactory.getInstance().getTemplate(inputStream.readUTF());
			animal.template = animal.getStatus().template;
			animal.getStatus().setSex(inputStream.readByte());
			short centimetersHigh = inputStream.readShort();
			short centimetersLong = inputStream.readShort();
			short centimetersWide = inputStream.readShort();
			animal.getStatus().inventoryId = inputStream.readLong();
			long bodyId = inputStream.readLong();
			animal.getStatus().bodyId = bodyId;
			animal.getStatus().body = BodyFactory.getBody(animal, animal.getStatus().template.getBodyType(), 
					centimetersHigh, centimetersLong, centimetersWide);
	        animal.getStatus().buildingId = inputStream.readLong();
	        if (animal.getStatus().buildingId != MiscConstants.NOID) 
	        {
	            try 
	            {
	                final Structure struct = Structures.getStructure(animal.getStatus().buildingId);
	                if (!struct.isFinalFinished()) 
	                {
	                    animal.setStructure(struct);
	                }
	                else 
	                {
	                    animal.getStatus().buildingId = MiscConstants.NOID;
	                }
	            }
	            catch (NoSuchStructureException nss) 
	            {
	                animal.getStatus().buildingId = MiscConstants.NOID;
	                logger.log(Level.INFO, "Could not find structure for " + animal.getName());
	                animal.setStructure(null);
	            }
	        }
	        animal.getStatus().stamina = inputStream.readShort();
	        animal.getStatus().hunger = inputStream.readShort();
	        animal.getStatus().nutrition = inputStream.readFloat();
	        animal.getStatus().thirst = inputStream.readShort();
	        animal.getStatus().dead = inputStream.readBoolean();
	        animal.getStatus().stealth = inputStream.readBoolean();
	        animal.getStatus().kingdom = inputStream.readByte();
	        animal.getStatus().age = inputStream.readInt();
	        animal.getStatus().lastPolledAge = inputStream.readLong();
	        animal.getStatus().fat = inputStream.readByte();
	        animal.getStatus().traits = inputStream.readLong();
	        if (animal.getStatus().traits != 0L) 
	        {
	        	animal.getStatus().setTraitBits(animal.getStatus().traits);
	        }
	        animal.dominator = inputStream.readLong();
	        animal.getStatus().mother = inputStream.readLong();
	        animal.getStatus().father = inputStream.readLong();
	        animal.getStatus().reborn = inputStream.readBoolean();
	        animal.getStatus().loyalty = inputStream.readFloat();
	        animal.getStatus().lastPolledLoyalty = inputStream.readLong();
	        animal.getStatus().offline = inputStream.readBoolean();
	        animal.getStatus().stayOnline = inputStream.readBoolean();
	        animal.getStatus().detectInvisCounter = inputStream.readShort();
	        animal.getStatus().disease = inputStream.readByte();
	        animal.getStatus().lastGroomed = inputStream.readLong();
	        final long hitchedTo = inputStream.readLong();
	        if (hitchedTo > 0L) // This shouldn't be true for animals associated with tokens...
	        {
	            try 
	            {
	                final Item vehicle = Items.getItem(hitchedTo);
	                final Vehicle vehic = Vehicles.getVehicle(vehicle);
	                if (vehic != null && vehic.addDragger(animal)) {
	                	animal.setHitched(vehic, true);
	                    final Seat driverseat = vehic.getPilotSeat();
	                    if (driverseat != null) {
	                        final float _r = (-vehicle.getRotation() + 180.0f) * 3.1415927f / 180.0f;
	                        final float _s = (float)Math.sin(_r);
	                        final float _c = (float)Math.cos(_r);
	                        final float xo = _s * -driverseat.offx - _c * -driverseat.offy;
	                        final float yo = _c * -driverseat.offx + _s * -driverseat.offy;
	                        final float nPosX = animal.getStatus().getPositionX() - xo;
	                        final float nPosY = animal.getStatus().getPositionY() - yo;
	                        final float nPosZ = animal.getStatus().getPositionZ() - driverseat.offz;
	                        animal.getStatus().setPositionX(nPosX);
	                        animal.getStatus().setPositionY(nPosY);
	                        animal.getStatus().setRotation(-vehicle.getRotation() + 180.0f);
	                        animal.getMovementScheme().setPosition(animal.getStatus().getPositionX(), animal.getStatus().getPositionY(), nPosZ, animal.getStatus().getRotation(), animal.getLayer());
	                    }
	                }
	                animal.getStatus().modtype = inputStream.readByte();
	                animal.setPetName(inputStream.readUTF());
	            }
	            catch (NoSuchItemException nsi) 
	            {
	                logger.log(Level.INFO, "Item " + hitchedTo + " missing for hitched " + animal.getWurmId() + " " + animal.getName());
	            }
	        }
		} catch (Exception e)
		{
            logger.log(Level.WARNING, e.getMessage(), e);
		}
		
		/*
        Creatures.getInstance().addCreature(toReturn, false, false);
        toReturn.loadSkills();
        toReturn.createPossessions();
        toReturn.getBody().createBodyParts();
        if (!toReturn.isAnimal() && createPossessions) {
            createBasicItems(toReturn);
            toReturn.wearItems();
        }
        if ((toReturn.isHorse() || toReturn.getTemplate().isBlackOrWhite) && Server.rand.nextInt(10) == 0) {
            setRandomColor(toReturn);
        }
        Creatures.getInstance().sendToWorld(toReturn);
        toReturn.createVisionArea();
        toReturn.save();
        if (reborn) {
            toReturn.getStatus().setReborn(true);
        }
        if (ctype != 0) {
            toReturn.getStatus().setType(ctype);
        }
        toReturn.getStatus().setKingdom(kingdom);
        if (kingdom == 3) {
            toReturn.setAlignment(-50.0f);
            toReturn.setDeity(Deities.getDeity(4));
            toReturn.setFaith(1.0f);
        }
        Server.getInstance().broadCastAction(String.valueOf(toReturn.getName()) + " has arrived.", toReturn, 10);
        if (toReturn.isUnique()) {
            Server.getInstance().broadCastSafe("Rumours of " + toReturn.getName() + " is starting to spread.");
            Servers.localServer.spawnedUnique();
        }
        return toReturn;
*/
		// TODO: Get skills and set for creature.
		
		// TODO: Save creature.
		
		// TODO: Process creature items.
		
	}

	// Helpers to work with private methods/fields.
	// ============================================
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
	
	private static long getPrivateLong(Object obj, String fieldName)
	{
		long toReturn = MiscConstants.NOID;
		try
		{
			Field field = ReflectionUtil.getField(obj.getClass(), fieldName);
			toReturn = ReflectionUtil.getPrivateField(obj, field);
		} catch (NoSuchFieldException | IllegalArgumentException | IllegalAccessException | ClassCastException e)
		{
			logger.log(Level.WARNING, "Unable to get private field " + fieldName + " from object of type "
					+ obj.getClass().getName() + ":" + e.getMessage(), e);
		}
		return toReturn;
	}
}
