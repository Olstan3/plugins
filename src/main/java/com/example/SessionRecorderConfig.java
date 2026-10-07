package com.example;

import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup("sessionrecorder")
public interface SessionRecorderConfig extends Config
{
	@ConfigItem(position = 1, keyName = "toggleKey", name = "Record start/stop hotkey",
		description = "Starts or stops a recording. Each recording is a new file.")
	default Keybind toggleKey()
	{
		return new Keybind(KeyEvent.VK_F10, 0);
	}

	@ConfigItem(position = 2, keyName = "markerKey", name = "Marker hotkey",
		description = "Writes a marker line, e.g. press it right before you start a new action so it is easy to find.")
	default Keybind markerKey()
	{
		return new Keybind(KeyEvent.VK_F11, 0);
	}

	@Range(min = 1, max = 15)
	@ConfigItem(position = 3, keyName = "scanRadius", name = "Object scan radius",
		description = "Objects within this many tiles are tracked, and any change in name/actions is logged.")
	default int scanRadius()
	{
		return 6;
	}

	@ConfigItem(position = 4, keyName = "logChat", name = "Log chat/game messages",
		description = "Include game messages (catch messages, errors) in the recording.")
	default boolean logChat()
	{
		return true;
	}
}
