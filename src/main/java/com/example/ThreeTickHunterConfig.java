package com.example;

import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup("threetickhunter")
public interface ThreeTickHunterConfig extends Config
{
	@ConfigItem(
		position = 1,
		keyName = "toggleKey",
		name = "Start/stop hotkey",
		description = "Press to start or stop the bot. Also your kill switch."
	)
	default Keybind toggleKey()
	{
		return new Keybind(KeyEvent.VK_F9, 0);
	}

	@Range(min = 1, max = 10)
	@ConfigItem(
		position = 2,
		keyName = "maxTraps",
		name = "Max traps",
		description = "How many traps to keep laid. 1 + Hunter level / 20 (max 5 on OSRS), but private servers vary."
	)
	default int maxTraps()
	{
		return 5;
	}

	@Range(min = 1, max = 10)
	@ConfigItem(
		position = 3,
		keyName = "tickDelay",
		name = "Ticks between actions",
		description = "Game ticks between each click. 3 = three-tick hunting."
	)
	default int tickDelay()
	{
		return 3;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		position = 4,
		keyName = "jitterChance",
		name = "Delay jitter %",
		description = "Chance (%) that an action is delayed by one extra tick, so the rhythm is not perfectly regular."
	)
	default int jitterChance()
	{
		return 5;
	}

	@Range(min = 1, max = 10)
	@ConfigItem(
		position = 5,
		keyName = "radius",
		name = "Trap radius",
		description = "Only traps within this many tiles of you are managed."
	)
	default int radius()
	{
		return 4;
	}

	@ConfigItem(
		position = 6,
		keyName = "trapItemId",
		name = "Trap item id",
		description = "Item id of the box trap in your inventory (10008 on OSRS)."
	)
	default int trapItemId()
	{
		return 10008;
	}

	@ConfigItem(
		position = 7,
		keyName = "trapNames",
		name = "Trap object names",
		description = "Comma separated names of trap objects in the world (set, caught and fallen traps)."
	)
	default String trapNames()
	{
		return "Box trap,Shaking box";
	}

	@Range(min = 10, max = 500)
	@ConfigItem(
		position = 8,
		keyName = "stallTicks",
		name = "Stall timeout (ticks)",
		description = "Stop automatically if nothing has been done for this many ticks."
	)
	default int stallTicks()
	{
		return 60;
	}
}
