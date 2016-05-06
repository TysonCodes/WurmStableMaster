# Stable Master for Wurm Unlimited

## Requirements
* Requires v0.18 of [Ago1024's server modloader](https://github.com/ago1024/WurmServerModLauncher/releases/latest) on the server.

## Installation
* Download StableMaster.zip
* Extract StableMaster.zip into Wurm Unlimited server folder 
(for example, C:/Program Files/SteamLibrary/SteamApps/common/Wurm Unlimited Dedicated Server). 
The StableMaster.jar file should end up in mods/StableMaster/StableMaster.jar.
* Optionally modify the properties.
* Start the server and connect.

## Background
Created in response to razoreqx's request on the Wurm Online forums. Also seemed like something I'd like 
to have to go exploring on a boat and still have a horse when I got there.

## Effects
* Adds a new NPC to the game, Stable Master.
  * By default the Stable Master is assigned an auto-generated template ID. This can be overridden to set a specific
  ID by setting "specifyStableMasterId=true" and setting the ID as "stableMasterId=".
* You can right-click the Stable Master while leading one or more animals and use the "Exchange animal" action to have the animals exchanged for Animal Tokens.
  * There is a fee (configurable) associated with exchanging each animal into an Animal Token.
  * The Animal Token weighs what the animal weighs (which can be excessive). There are configuration options to limit this to a minimum ("animalTokenMinimumWeightGrams") and a maximum ("animalTokenMaximumWeightGrams") value. Note that the maximum is applied after the minimum in case the minimum is larger than the maximum.
  * The Animal Token takes up the volume of a corpse by default but is configurable (X, Y, Z in centimeters.)
  * By default the Animal Token is assigned an auto-generated template ID. This can be overridden to set a specific
  ID by setting "specifyAnimalTokenId=true" and setting the ID as "animalTokenId=".
* You can right-click on the Animal Token and use the "Redeem animal token" action to turn the token back into a animal at your current location.
* The Animal Token can be loaded onto boats by activating the token, right-clicking the boat and selecting the "Load animal token" action.
  * By default the token cannot be loaded onto rowboats or sailboats but this is configurable with 'enableSmallBoatsLoad=true'.
* The Animal Token can be unloaded from a boat by right-clicking on the token in the boat inventory and selecting the "Unload animal token" action.
* Disabled by default, there is an option to enable directly right-clicking on a led animal and using the "Exchange animal" action for no cost. To enable set "enableNoNpcExchange=true"
