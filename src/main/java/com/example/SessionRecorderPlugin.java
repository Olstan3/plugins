package com.example;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Item;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.HotkeyListener;

/**
 * Records what happens in the game to a JSON-lines file so it can be analysed later.
 * Files go to ~/.runelite/session-recorder/.
 */
@Slf4j
@PluginDescriptor(
	name = "Session Recorder",
	description = "Records clicks, objects, animations and inventory changes to a file for analysis",
	tags = {"record", "log", "debug"}
)
public class SessionRecorderPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private SessionRecorderConfig config;

	@Inject
	private KeyManager keyManager;

	@Inject
	private Gson gson;

	private BufferedWriter out;
	private File file;
	private int tick;
	private final Map<WorldPoint, String> seen = new HashMap<>();

	private final HotkeyListener toggle = new HotkeyListener(() -> config.toggleKey())
	{
		@Override
		public void hotkeyPressed()
		{
			if (out == null)
			{
				begin();
			}
			else
			{
				end();
			}
		}
	};

	private final HotkeyListener marker = new HotkeyListener(() -> config.markerKey())
	{
		@Override
		public void hotkeyPressed()
		{
			event("marker");
		}
	};

	@Provides
	SessionRecorderConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SessionRecorderConfig.class);
	}

	@Override
	protected void startUp()
	{
		keyManager.registerKeyListener(toggle);
		keyManager.registerKeyListener(marker);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(toggle);
		keyManager.unregisterKeyListener(marker);
		end();
	}

	private void begin()
	{
		try
		{
			File dir = new File(RuneLite.RUNELITE_DIR, "session-recorder");
			Files.createDirectories(dir.toPath());
			file = new File(dir, "session-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".jsonl");
			out = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8);
			seen.clear();
			event("start");
			message("Recording to " + file.getAbsolutePath());
		}
		catch (IOException e)
		{
			log.warn("Could not start recording", e);
			out = null;
		}
	}

	private void end()
	{
		if (out == null)
		{
			return;
		}
		event("stop");
		try
		{
			out.close();
		}
		catch (IOException e)
		{
			log.warn("Could not close recording", e);
		}
		out = null;
		message("Recording saved: " + file.getAbsolutePath());
	}

	private void message(String text)
	{
		if (client.getGameState() != null)
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", text, null);
		}
	}

	private void event(String type, Object... kv)
	{
		if (out == null)
		{
			return;
		}
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("tick", tick);
		m.put("ms", System.currentTimeMillis());
		m.put("type", type);
		for (int i = 0; i + 1 < kv.length; i += 2)
		{
			m.put(String.valueOf(kv[i]), kv[i + 1]);
		}
		try
		{
			out.write(gson.toJson(m));
			out.newLine();
			out.flush();
		}
		catch (IOException e)
		{
			log.warn("Recording write failed", e);
		}
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		tick++;
		if (out == null)
		{
			return;
		}
		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return;
		}
		WorldPoint wp = me.getWorldLocation();
		event("tick",
			"x", wp.getX(), "y", wp.getY(), "plane", wp.getPlane(),
			"anim", me.getAnimation(), "pose", me.getPoseAnimation(), "idlePose", me.getIdlePoseAnimation(),
			"interacting", me.getInteracting() == null ? null : me.getInteracting().getName());
		scanObjects(me);
	}

	/** Logs objects near the player when they appear or their name/actions change (e.g. a trap going from set to caught). */
	private void scanObjects(Player me)
	{
		Scene scene = client.getScene();
		Tile[][][] tiles = scene.getTiles();
		int plane = me.getWorldLocation().getPlane();
		LocalPoint lp = me.getLocalLocation();
		int r = config.scanRadius();
		Map<WorldPoint, String> now = new HashMap<>();

		for (int x = lp.getSceneX() - r; x <= lp.getSceneX() + r; x++)
		{
			for (int y = lp.getSceneY() - r; y <= lp.getSceneY() + r; y++)
			{
				if (x < 0 || y < 0 || x >= tiles[plane].length || y >= tiles[plane][x].length)
				{
					continue;
				}
				Tile tile = tiles[plane][x][y];
				if (tile == null || tile.getGameObjects() == null)
				{
					continue;
				}
				for (GameObject obj : tile.getGameObjects())
				{
					if (obj == null || !obj.getSceneMinLocation().equals(tile.getSceneLocation()))
					{
						continue;
					}
					ObjectComposition def = client.getObjectDefinition(obj.getId());
					int id = obj.getId();
					if (def.getImpostorIds() != null && def.getImpostor() != null)
					{
						def = def.getImpostor();
					}
					if (def.getName() == null || def.getName().equals("null"))
					{
						continue;
					}
					WorldPoint at = obj.getWorldLocation();
					String desc = def.getName() + " | " + Arrays.toString(def.getActions()) + " | id=" + id + " impostor=" + def.getId();
					now.put(at, desc);
					if (!desc.equals(seen.get(at)))
					{
						event("object", "x", at.getX(), "y", at.getY(), "desc", desc);
					}
				}
			}
		}
		for (WorldPoint gone : seen.keySet())
		{
			if (!now.containsKey(gone))
			{
				event("objectGone", "x", gone.getX(), "y", gone.getY(), "was", seen.get(gone));
			}
		}
		seen.clear();
		seen.putAll(now);
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		event("click",
			"option", e.getMenuOption(), "target", e.getMenuTarget(), "action", String.valueOf(e.getMenuAction()),
			"id", e.getId(), "param0", e.getParam0(), "param1", e.getParam1());
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		if (e.getActor() == client.getLocalPlayer())
		{
			event("animation", "id", e.getActor().getAnimation());
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged e)
	{
		if (out == null)
		{
			return;
		}
		StringBuilder sb = new StringBuilder();
		for (Item item : e.getItemContainer().getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				sb.append(item.getId()).append('x').append(item.getQuantity()).append(' ');
			}
		}
		event("items", "container", e.getContainerId(), "items", sb.toString().trim());
	}

	@Subscribe
	public void onStatChanged(StatChanged e)
	{
		event("stat", "skill", String.valueOf(e.getSkill()), "xp", e.getXp(), "level", e.getLevel());
	}

	@Subscribe
	public void onChatMessage(ChatMessage e)
	{
		if (config.logChat())
		{
			event("chat", "chatType", String.valueOf(e.getType()), "text", e.getMessage());
		}
	}
}
