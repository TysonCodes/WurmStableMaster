package org.tmarchuk.wurmunlimited.server.stablemaster;


/**
 * Created by Tyson Marchuk on 2016-04-29.
 */

// From Wurm Unlimited Server
import com.wurmonline.server.DbConnector;
import com.wurmonline.server.MiscConstants;
import com.wurmonline.server.Players;
import com.wurmonline.server.behaviours.ActionStack;
import com.wurmonline.server.behaviours.MethodsItems;
import com.wurmonline.server.behaviours.Vehicles;
import com.wurmonline.server.creatures.CombatHandler;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.creatures.CreatureStatus;
import com.wurmonline.server.creatures.CreatureTemplateFactory;
import com.wurmonline.server.creatures.Creatures;
import com.wurmonline.server.creatures.Offspring;
import com.wurmonline.server.creatures.TransferredCreatureHelper;
import com.wurmonline.server.intra.PlayerTransfer;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemMetaData;
import com.wurmonline.server.skills.Skill;
import com.wurmonline.server.utils.DbUtilities;

//From Ago's modloader
import org.gotti.wurmunlimited.modloader.ReflectionUtil;

import java.io.DataInputStream;
//Base java
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;
import java.util.Set;
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
			logger.log(Level.WARNING, creat.getName() + ":" + e.getMessage(), e);
		}
	}
	
	// Send the minimum data associated with a creature to 'outputStream'.
	public static void toStream(Creature animal, DataOutputStream outputStream) throws IOException
	{
		Connection dbcon = null;
		PreparedStatement ps = null;
		ResultSet rs = null;

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
		
		// Send row data from CREATURES table.
        try
		{
			dbcon = DbConnector.getCreatureDbCon();
			ps = dbcon.prepareStatement("SELECT * FROM CREATURES WHERE WURMID=?");
			ps.setLong(1, animal.getWurmId());
			rs = ps.executeQuery();
			rs.next();
			
			// Write the values to the output stream.
			outputStream.writeLong(rs.getLong("WURMID"));
			outputStream.writeUTF(rs.getString("NAME"));
			outputStream.writeUTF(rs.getString("TEMPLATENAME"));
			outputStream.writeByte(rs.getByte("SEX"));
			outputStream.writeShort(rs.getShort("CENTIMETERSHIGH"));
			outputStream.writeShort(rs.getShort("CENTIMETERSLONG"));
			outputStream.writeShort(rs.getShort("CENTIMETERSWIDE"));
			outputStream.writeLong(rs.getLong("INVENTORYID"));
			outputStream.writeLong(rs.getLong("BODYID"));
			outputStream.writeLong(rs.getLong("BUILDINGID"));
			outputStream.writeShort(rs.getShort("STAMINA"));
			outputStream.writeShort(rs.getShort("HUNGER"));
			outputStream.writeFloat(rs.getFloat("NUTRITION"));
			outputStream.writeShort(rs.getShort("THIRST"));
			outputStream.writeBoolean(rs.getBoolean("DEAD"));
			outputStream.writeBoolean(rs.getBoolean("STEALTH"));
			outputStream.writeByte(rs.getByte("KINGDOM"));
			outputStream.writeInt(rs.getInt("AGE"));
			outputStream.writeLong(rs.getLong("LASTPOLLEDAGE"));
			outputStream.writeByte(rs.getByte("FAT"));
			outputStream.writeLong(rs.getLong("TRAITS"));
			outputStream.writeLong(rs.getLong("DOMINATOR"));
			outputStream.writeLong(rs.getLong("MOTHER"));
			outputStream.writeLong(rs.getLong("FATHER"));
			outputStream.writeBoolean(rs.getBoolean("REBORN"));
			outputStream.writeFloat(rs.getFloat("LOYALTY"));
			outputStream.writeLong(rs.getLong("LASTPOLLEDLOYALTY"));
			outputStream.writeBoolean(rs.getBoolean("OFFLINE"));
			outputStream.writeBoolean(rs.getBoolean("STAYONLINE"));
			outputStream.writeShort(rs.getShort("DETECTIONSECS"));
			outputStream.writeByte(rs.getByte("DISEASE"));
			outputStream.writeLong(rs.getLong("LASTGROOMED"));
			outputStream.writeLong(rs.getLong("VEHICLE"));
			outputStream.writeByte(rs.getByte("TYPE"));
			outputStream.writeUTF(rs.getString("PETNAME"));

			/*	
				These don't appear to be used
			    DAMAGE                  FLOAT         ,
			    SIZEX                   INT           ,
			    SIZEY                   INT           ,
			    SIZEZ                   INT           ,
			    SPAWNPOINT              INT           NOT NULL DEFAULT -1,
			    TEMPLATEID              INT           NOT NULL DEFAULT -1,
			    SETTINGS                INT           NOT NULL DEFAULT 0,
			 */	

		} catch (SQLException e)
		{
			logger.log(Level.WARNING, "Failed to get animal from database " + ":" + e.getMessage(), e);
		} finally
		{
			// Cleanup database connection.
            DbUtilities.closeDatabaseObjects(ps, null);
            DbConnector.returnConnection(dbcon);
		}

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
			TransferredCreatureHelper.createOffspring(mother, father, traits, daysLeft, false);
		}
		
		// TODO: Get creature data and create creature.
		long creatureId = inputStream.readLong();
		try
		{
			Creature animal = new Creature(creatureId);
			animal.setName(inputStream.readUTF());
			TransferredCreatureHelper.setCreatureTemplate(animal, inputStream.readUTF());
			animal.getStatus().setSex(inputStream.readByte());
			short centimetersHigh = inputStream.readShort();
			short centimetersLong = inputStream.readShort();
			short centimetersWide = inputStream.readShort();
			long inventoryId = inputStream.readLong();
			long bodyId = inputStream.readLong();
			TransferredCreatureHelper.setCreatureBody(animal, bodyId, centimetersHigh, centimetersLong, centimetersWide);
			TransferredCreatureHelper.setCreatureInventoryAndBuildingIds(animal, inventoryId, inputStream.readLong());
//	        statusHolder.getStatus().stamina = (rs.getShort("STAMINA") & 0xFFFF);
//	        statusHolder.getStatus().hunger = (rs.getShort("HUNGER") & 0xFFFF);
//	        statusHolder.getStatus().thirst = (rs.getShort("THIRST") & 0xFFFF);

		} catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
/*			outputStream.writeShort(rs.getShort("STAMINA"));
			outputStream.writeShort(rs.getShort("HUNGER"));
			outputStream.writeFloat(rs.getFloat("NUTRITION"));
			outputStream.writeShort(rs.getShort("THIRST"));
			outputStream.writeBoolean(rs.getBoolean("DEAD"));
			outputStream.writeBoolean(rs.getBoolean("STEALTH"));
			outputStream.writeByte(rs.getByte("KINGDOM"));
			outputStream.writeInt(rs.getInt("AGE"));
			outputStream.writeLong(rs.getLong("LASTPOLLEDAGE"));
			outputStream.writeByte(rs.getByte("FAT"));
			outputStream.writeLong(rs.getLong("TRAITS"));
			outputStream.writeLong(rs.getLong("DOMINATOR"));
			outputStream.writeLong(rs.getLong("MOTHER"));
			outputStream.writeLong(rs.getLong("FATHER"));
			outputStream.writeBoolean(rs.getBoolean("REBORN"));
			outputStream.writeFloat(rs.getFloat("LOYALTY"));
			outputStream.writeLong(rs.getLong("LASTPOLLEDLOYALTY"));
			outputStream.writeBoolean(rs.getBoolean("OFFLINE"));
			outputStream.writeBoolean(rs.getBoolean("STAYONLINE"));
			outputStream.writeShort(rs.getShort("DETECTIONSECS"));
			outputStream.writeByte(rs.getByte("DISEASE"));
			outputStream.writeLong(rs.getLong("LASTGROOMED"));
			outputStream.writeLong(rs.getLong("VEHICLE"));
			outputStream.writeByte(rs.getByte("TYPE"));
			outputStream.writeUTF(rs.getString("PETNAME"));
*/		
		// TODO: Get skills and set for creature.
		
		// TODO: Save creature.
		
		// TODO: Process creature items.
		/*
        statusHolder.getStatus().modtype = rs.getByte("TYPE");
        statusHolder.getStatus().kingdom = rs.getByte("KINGDOM");
        statusHolder.getStatus().dead = rs.getBoolean("DEAD");
        statusHolder.getStatus().stealth = rs.getBoolean("STEALTH");
        statusHolder.getStatus().age = rs.getInt("AGE");
        statusHolder.getStatus().fat = rs.getByte("FAT");
        statusHolder.getStatus().lastPolledAge = rs.getLong("LASTPOLLEDAGE");
        statusHolder.dominator = rs.getLong("DOMINATOR");
        statusHolder.getStatus().reborn = rs.getBoolean("REBORN");
        statusHolder.getStatus().loyalty = rs.getFloat("LOYALTY");
        statusHolder.getStatus().lastPolledLoyalty = rs.getLong("LASTPOLLEDLOYALTY");
        statusHolder.getStatus().detectInvisCounter = rs.getShort("DETECTIONSECS");
        statusHolder.getStatus().traits = rs.getLong("TRAITS");
        if (statusHolder.getStatus().traits != 0L) {
            statusHolder.getStatus().setTraitBits(statusHolder.getStatus().traits);
        }
        statusHolder.getStatus().mother = rs.getLong("MOTHER");
        statusHolder.getStatus().father = rs.getLong("FATHER");
        statusHolder.getStatus().nutrition = rs.getFloat("NUTRITION");
        statusHolder.getStatus().disease = rs.getByte("DISEASE");
        if (statusHolder.getStatus().buildingId != -10L) {
            try {
                final Structure struct = Structures.getStructure(statusHolder.getStatus().buildingId);
                if (!struct.isFinalFinished()) {
                    statusHolder.setStructure(struct);
                }
                else {
                    statusHolder.getStatus().buildingId = -10L;
                }
            }
            catch (NoSuchStructureException nss) {
                statusHolder.getStatus().buildingId = -10L;
                Creatures.logger.log(Level.INFO, "Could not find structure for " + statusHolder.getName());
                statusHolder.setStructure(null);
            }
        }
        statusHolder.getStatus().lastGroomed = rs.getLong("LASTGROOMED");
        statusHolder.getStatus().offline = rs.getBoolean("OFFLINE");
        statusHolder.getStatus().stayOnline = rs.getBoolean("STAYONLINE");
        final String petName = rs.getString("PETNAME");
        statusHolder.setPetName(petName);
        statusHolder.calculateSize();
        final long hitchedTo = rs.getLong("VEHICLE");
        if (hitchedTo > 0L) {
            try {
                final Item vehicle = Items.getItem(hitchedTo);
                final Vehicle vehic = Vehicles.getVehicle(vehicle);
                if (vehic != null && vehic.addDragger(statusHolder)) {
                    statusHolder.setHitched(vehic, true);
                    final Seat driverseat = vehic.getPilotSeat();
                    if (driverseat != null) {
                        final float _r = (-vehicle.getRotation() + 180.0f) * 3.1415927f / 180.0f;
                        final float _s = (float)Math.sin(_r);
                        final float _c = (float)Math.cos(_r);
                        final float xo = _s * -driverseat.offx - _c * -driverseat.offy;
                        final float yo = _c * -driverseat.offx + _s * -driverseat.offy;
                        final float nPosX = statusHolder.getStatus().getPositionX() - xo;
                        final float nPosY = statusHolder.getStatus().getPositionY() - yo;
                        final float nPosZ = statusHolder.getStatus().getPositionZ() - driverseat.offz;
                        statusHolder.getStatus().setPositionX(nPosX);
                        statusHolder.getStatus().setPositionY(nPosY);
                        statusHolder.getStatus().setRotation(-vehicle.getRotation() + 180.0f);
                        statusHolder.getMovementScheme().setPosition(statusHolder.getStatus().getPositionX(), statusHolder.getStatus().getPositionY(), nPosZ, statusHolder.getStatus().getRotation(), statusHolder.getLayer());
                    }
                }
            }
            catch (NoSuchItemException nsi) {
                Creatures.logger.log(Level.INFO, "Item " + hitchedTo + " missing for hitched " + id + " " + name);
            }
*/		
/*
        final Creature toReturn = new Creature(WurmID);
        if (name.length() > 0) {
            toReturn.setName(name);
        }
        if (toReturn.getTemplate().isRoyalAspiration()) {
            if (toReturn.getTemplate().getTemplateId() == 62) {
                kingdom = 1;
            }
            else if (toReturn.getTemplate().getTemplateId() == 63) {
                kingdom = 3;
            }
        }
        if (reborn) {
            toReturn.getStatus().reborn = true;
        }
        if (layer > 0) {
            toReturn.pushToFloorLevel(layer);
        }
        else {
            toReturn.setPositionZ(toReturn.calculatePosZ());
        }
        if (age <= 0) {
            toReturn.getStatus().age = (int)(Server.rand.nextFloat() * Math.min(48, toReturn.getTemplate().getMaxAge()));
        }
        else {
            toReturn.getStatus().age = age;
        }
        if (toReturn.isGhost() || toReturn.isKingdomGuard() || reborn) {
            toReturn.getStatus().age = 12;
        }
        if (ctype != 0) {
            toReturn.getStatus().modtype = ctype;
        }
        if (toReturn.isUnique()) {
            toReturn.getStatus().age = 12 + (int)(Server.rand.nextFloat() * (toReturn.getTemplate().getMaxAge() - 12));
        }
        toReturn.getStatus().kingdom = kingdom;
        if (Kingdoms.getKingdom(kingdom) != null && Kingdoms.getKingdom(kingdom).getTemplate() == 3) {
            toReturn.setAlignment(-50.0f);
            toReturn.setDeity(Deities.getDeity(4));
            toReturn.setFaith(1.0f);
        }
        toReturn.setSex(gender);
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
        return toReturn;*/
		
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
