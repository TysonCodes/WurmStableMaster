package org.tmarchuk.wurmunlimited.server.stablemaster;

/**
 * Created by Tyson Marchuk on 2016-04-30.
 */

// From Wurm Commmon
import com.wurmonline.shared.constants.SoundNames;

//From Wurm Unlimited Server
import com.wurmonline.server.bodys.BodyTemplate;
import com.wurmonline.server.creatures.CreatureTemplate;
import com.wurmonline.server.creatures.CreatureTemplateFactory;
import com.wurmonline.server.creatures.CreatureTypes;
import com.wurmonline.server.skills.SkillList;
import com.wurmonline.server.skills.Skills;
import com.wurmonline.server.skills.SkillsFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

// From Ago's modloader
import org.gotti.wurmunlimited.modloader.ReflectionUtil;
import org.gotti.wurmunlimited.modsupport.IdFactory;
import org.gotti.wurmunlimited.modsupport.IdType;

public class StableMaster implements CreatureTypes
{
	// Constants
	private static String STABLE_MASTER_TEMPLATE_BUILDER_ID = "stableMaster";
	
	// Template ID to use for the stable master. Might be set or auto-generated.
	private final int stableMasterTemplateId;

	// Simple parameters for the template
	private final String tName = "Stable master";
	private final String tDescription = "An expert with horses and other mounts. Able to help load mounts on ships.";
	private final String tModelName = "model.creature.humanoid.human.salesman";
	private final short tVisionTiles = 2;	// How  many tiles away it can see.
	private final byte tSex = 0;			// I don't understand. This appears to be male but female can be spawned...
	private final short tHeightCentimeters = 180;
	private final short tLengthCentimeters = 20;
	private final short tWidthCentimeters = 35;
	private final float tNaturalArmour = 1.0f;
	private final float tHandDamage = 1.0f;
	private final float tKickDamage = 2.0f;
	private final float tBiteDamage = 0.0f;
	private final float tHeadDamage = 0.0f;
	private final float tBreathDamage = 0.0f;
	private final float tSpeed = 0.8f;
	private final int tMoveRate = 0;
	private final int[] tItemsButchered = new int[0]; 	// Butchering gets inventory?
	private final int tMaxHuntDistanceTiles = 3;
	private final int tAggressivity = 0;
	
	public StableMaster(boolean specifyId, int id)
	{
		if (specifyId)
		{
			stableMasterTemplateId = id;
		}
		else
		{
			stableMasterTemplateId = IdFactory.getIdFor(STABLE_MASTER_TEMPLATE_BUILDER_ID, IdType.CREATURETEMPLATE);
		}
	}
	
	public void onItemTemplatesCreated() 
	{
		// This NPC is largely based on the Salesman NPC for now.
		final int[] tTypes = {C_TYPE_SENTINEL, C_TYPE_INVULNERABLE, C_TYPE_SWIMMING, C_TYPE_HUMAN };
		final Skills tSkills = SkillsFactory.createSkills(tName);
		tSkills.learnTemp(SkillList.BODY_STRENGTH, 15.0f);
		tSkills.learnTemp(SkillList.BODY_CONTROL, 15.0f);
		tSkills.learnTemp(SkillList.BODY_STAMINA, 10.0f);
		tSkills.learnTemp(SkillList.MIND_LOGICAL, 30.0f);
		tSkills.learnTemp(SkillList.MIND_SPEED, 30.0f);
		tSkills.learnTemp(SkillList.SOUL_STRENGTH, 99.0f);
		tSkills.learnTemp(SkillList.SOUL_DEPTH, 4.0f);
		tSkills.learnTemp(SkillList.WEAPONLESS_FIGHTING, 40.0f);

		try
		{
			final CreatureTemplate temp = createCreatureTemplate(stableMasterTemplateId, tName, tDescription, 
				tModelName, tTypes, BodyTemplate.TYPE_HUMAN, tSkills, 
				tVisionTiles, tSex, tHeightCentimeters, tLengthCentimeters, tWidthCentimeters, 
				SoundNames.DEATH_MALE_SND, SoundNames.DEATH_FEMALE_SND, SoundNames.HIT_MALE_SND, 
				SoundNames.HIT_FEMALE_SND, tNaturalArmour, tHandDamage, tKickDamage, tBiteDamage, 
				tHeadDamage, tBreathDamage, tSpeed, 
				tMoveRate, tItemsButchered, tMaxHuntDistanceTiles, tAggressivity);

			Method setBaseCombatRating = ReflectionUtil.getMethod(CreatureTemplate.class, "setBaseCombatRating");
			ReflectionUtil.callPrivateMethod(temp, setBaseCombatRating, 70.0f);
			Field hasHands = ReflectionUtil.getField(CreatureTemplate.class, "hasHands");
			ReflectionUtil.setPrivateField(temp, hasHands, true);
		} catch (IOException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | ClassCastException | NoSuchFieldException e) 
		{
			throw new RuntimeException(e);
		}
	}
	
	public int getTemplateId()
	{
		return this.stableMasterTemplateId;
	}

	private CreatureTemplate createCreatureTemplate(final int id, final String name, final String longDesc, final String modelName, final int[] types, final byte bodyType, final Skills skills, final short vision, final byte sex, final short centimetersHigh, final short centimetersLong,
			final short centimetersWide, final String deathSndMale, final String deathSndFemale, final String hitSndMale, final String hitSndFemale, final float naturalArmour, final float handDam, final float kickDam, final float biteDam, final float headDam, final float breathDam,
			final float speed, final int moveRate, final int[] itemsButchered, final int maxHuntDist, final int aggress) throws IOException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException {

		return ReflectionUtil.callPrivateMethod(CreatureTemplateFactory.getInstance(), ReflectionUtil.getMethod(CreatureTemplateFactory.class, "createCreatureTemplate"), id, name, longDesc, modelName, types, bodyType, skills, vision, sex, centimetersHigh, centimetersLong, centimetersWide,
				deathSndMale, deathSndFemale, hitSndMale, hitSndFemale, naturalArmour, handDam, kickDam, biteDam, headDam, breathDam, speed, moveRate, itemsButchered, maxHuntDist, aggress);
	}
}
