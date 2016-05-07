package com.wurmonline.server.creatures;

import com.wurmonline.server.bodys.BodyFactory;

public class TransferredCreatureHelper
{

	TransferredCreatureHelper()
	{}
	
	public static Offspring createOffspring(long mother, long father, long traits, byte daysLeft, boolean loaded)
	{
		return new Offspring(mother, father, traits, daysLeft, loaded);
	}
	
	public static void setCreatureTemplate(Creature creat, String templateName) throws Exception
	{
		creat.getStatus().template = CreatureTemplateFactory.getInstance().getTemplate(templateName);
		creat.template = creat.getStatus().template;
	}

	public static void setCreatureBody(Creature animal, long bodyId, short centimetersHigh, short centimetersLong,
			short centimetersWide) throws Exception
	{
		animal.getStatus().bodyId = bodyId;
		animal.getStatus().body = BodyFactory.getBody(animal, animal.getStatus().template.getBodyType(), 
				centimetersHigh, centimetersLong, centimetersWide);
	}

	public static void setCreatureInventoryAndBuildingIds(Creature animal, long inventoryId, long buildingId)
	{
        animal.getStatus().inventoryId = inventoryId;
        animal.getStatus().buildingId = buildingId;
	}
}
