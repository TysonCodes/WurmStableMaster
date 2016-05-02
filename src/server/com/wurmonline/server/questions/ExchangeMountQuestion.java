package com.wurmonline.server.questions;


/**
 * Created by Tyson Marchuk on 2016-05-01.
 */

// From Wurm Unlimited Server
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.economy.Change;
import com.wurmonline.server.economy.Economy;

// Base Java
import java.io.IOException;
import java.util.Properties;

// This mod
import org.tmarchuk.wurmunlimited.server.stablemaster.ExchangeAction;

public class ExchangeMountQuestion extends Question
{
	// Constant
	private static final int EXCHANGE_MOUNT_QUESTION_TYPE = 1000;	// Should really be in QuestionTypes but I don't know how to put it there.

	// Configuration
	private final int exchangeMountCostIrons;
	private final Change exchangeMountCost;
	private final String answerGroup = "exchange";
	private final Creature mount;
	private static final int dialogWidth = 300;
	private static final int dialogHeight = 300;
	private static final boolean dialogResizeable = true;
	private static final boolean dialogCloseable = true;
	private static final int dialogRed = 200;
	private static final int dialogGreen = 200;
	private static final int dialogBlue = 200;
	private static final String dialogTitle = "Exchange Mount";
	private static final String dialogDescription = "Do you want to exchange your mount?"; 
	
	public ExchangeMountQuestion(final Creature aResponder, final Creature mount, int exchangeMountCostIrons)
	{
		super(aResponder, dialogTitle, dialogDescription, 
				EXCHANGE_MOUNT_QUESTION_TYPE, aResponder.getWurmId());
		this.exchangeMountCostIrons = exchangeMountCostIrons;
		this.mount = mount;
		this.exchangeMountCost = Economy.getEconomy().getChangeFor((long) exchangeMountCostIrons);
	}

	@Override
	public void answer(Properties props)
	{
		Creature resp = this.getResponder();
		String rAnswer = props.getProperty(answerGroup);
		
		if (rAnswer != null && rAnswer.equals("true"))
		{
			// Attempt to charge them.
			try
			{
				if (resp.chargeMoney(this.exchangeMountCostIrons))
				{
					// Thank them.
					resp.getCommunicator().sendNormalServerMessage("Thank you! Here is your token.");
					
					// Process the conversion.
					ExchangeAction.exchangeMountForToken(resp, mount);
				}
				else
				{
					// Apologize that they don't have enough money.
					resp.getCommunicator().sendNormalServerMessage("Sorry, you don't have enough money.");
				}
			} catch (IOException e)
			{
				throw new RuntimeException(e);
			}
		}
		else
		{
			// Ask them to come back later.
			resp.getCommunicator().sendNormalServerMessage("Come back anytime.");
		}
	}

	@Override
	public void sendQuestion()
	{
		// Convenience
		Creature resp = this.getResponder();
		
		// Determine how much money the responder (player) has and convert to Change.
		final long rBankAmountIrons = resp.getMoney();
		final Change rBankAmount = Economy.getEconomy().getChangeFor(rBankAmountIrons);

		// Come up with message.
        final StringBuilder content = new StringBuilder();
        content.append(this.getBmlHeader());
        content.append("text{text='Well met " + resp.getName() + "!'}text{text=''}");
        content.append("text{text='I can help you turn your mount into a token so you can load it on a "
        		+ "ship and take it with you on your journey. For this amazing feat I only charge " + 
        		this.exchangeMountCost.getChangeShortString() + "'}text{text=''}");
		
        // See if they have enough.
        if (rBankAmountIrons < this.exchangeMountCostIrons)
        {
        	String rBankAmountString = "";
        	if (rBankAmountIrons == 0L)
        	{
        		rBankAmountString = "have no money";
        	}
        	else
        	{
        		rBankAmountString = "only have " + rBankAmount.getChangeShortString();
        	}
        	// Show them a message where the cost is shown but they are told they don't have enough.
            content.append("text{text='Unfortunately you " + rBankAmountString + 
            		". Please come back when you have enough.'}text{text=''}");
            content.append(this.createOkAnswerButton());
        }
        else
        {
        	// Show them a message where the cost is shown, their bank is shown and they are asked if 
        	// they want to proceed.
            content.append("text{text='You currently have " + rBankAmount.getChangeShortString() + 
            		"in your bank.'}text{text=''}");
            content.append("text{type='italic';text='Would you like to exchange your mount for a token?'}");
            content.append("radio{ group='" + answerGroup + "'; id='true';text='Yes'}");
            content.append("radio{ group='" + answerGroup + "'; id='false';text='No';selected='true'}");
            content.append(this.createAnswerButton2());
        }
        resp.getCommunicator().sendBml(dialogWidth, dialogHeight, dialogResizeable, dialogCloseable, 
        		content.toString(), dialogRed, dialogGreen, dialogBlue, dialogTitle);
	}
}
