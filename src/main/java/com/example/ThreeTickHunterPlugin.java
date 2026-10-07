package com.example;

import com.google.inject.Provides;
import java.awt.Canvas;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;

/**
 * Keeps a set of box traps laid, checked and reset on a fixed game-tick rhythm (3 ticks by default).
 *
 * Clicks are sent to the game canvas as ordinary mouse events, so only use this on a private
 * server that allows automation.
 */
@Slf4j
@PluginDescriptor(
	name = "Three Tick Hunter",
	description = "Automated box trap hunting on a 3-tick rhythm",
	tags = {"hunter", "box trap", "chinchompa", "bot", "automation"}
)
public class ThreeTickHunterPlugin extends Plugin
{
	private static final int INVENTORY_SIZE = 28;
	private static final int PENDING_LAY_TICKS = 6;

	@Inject
	private Client client;

	@Inject
	private ThreeTickHunterConfig config;

	@Inject
	private KeyManager keyManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ThreeTickHunterOverlay overlay;

	private final Random random = new Random();
	private final Map<WorldPoint, Integer> pendingLays = new HashMap<>();
	private ExecutorService mouse;

	@Getter
	private volatile boolean running;
	@Getter
	private volatile String status = "Stopped";

	private int tick;
	private int cooldown;
	private int lastActionTick;

	private final HotkeyListener hotkey = new HotkeyListener(() -> config.toggleKey())
	{
		@Override
		public void hotkeyPressed()
		{
			if (running)
			{
				stop("Stopped by hotkey");
			}
			else
			{
				start();
			}
		}
	};

	@Provides
	ThreeTickHunterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ThreeTickHunterConfig.class);
	}

	@Override
	protected void startUp()
	{
		mouse = Executors.newSingleThreadExecutor();
		overlayManager.add(overlay);
		keyManager.registerKeyListener(hotkey);
	}

	@Override
	protected void shutDown()
	{
		running = false;
		keyManager.unregisterKeyListener(hotkey);
		overlayManager.remove(overlay);
		mouse.shutdownNow();
		pendingLays.clear();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (running && event.getGameState() != GameState.LOGGED_IN && event.getGameState() != GameState.LOADING)
		{
			stop("Logged out");
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tick++;
		if (!running)
		{
			return;
		}

		Player me = client.getLocalPlayer();
		if (me == null)
		{
			return;
		}

		if (tick - lastActionTick > config.stallTicks())
		{
			stop("Stalled, nothing to do for " + config.stallTicks() + " ticks");
			return;
		}

		if (cooldown > 0)
		{
			cooldown--;
			return;
		}

		if (me.getPoseAnimation() != me.getIdlePoseAnimation())
		{
			status = "Walking";
			return;
		}

		List<Trap> traps = findTraps(me);
		Set<WorldPoint> visible = traps.stream().map(t -> t.location).collect(Collectors.toSet());
		pendingLays.entrySet().removeIf(e -> tick - e.getValue() > PENDING_LAY_TICKS || visible.contains(e.getKey()));

		Widget inventory = client.getWidget(ComponentID.INVENTORY_CONTAINER);
		if (inventory == null || inventory.isHidden())
		{
			status = "Open the inventory tab";
			return;
		}

		// Caught traps first: they time out.
		Trap target = nearest(traps, "Check", me);
		if (target != null)
		{
			if (usedSlots(inventory) >= INVENTORY_SIZE)
			{
				stop("Inventory full");
				return;
			}
			status = "Checking trap";
			if (clickShape(target.object.getClickbox()))
			{
				acted();
			}
			return;
		}

		target = nearest(traps, "Reset", me);
		if (target != null)
		{
			status = "Resetting trap";
			if (clickShape(target.object.getClickbox()))
			{
				acted();
			}
			return;
		}

		if (traps.size() + pendingLays.size() >= config.maxTraps())
		{
			status = "Waiting for catches";
			return;
		}

		Rectangle item = findInventoryItem(inventory, config.trapItemId());
		if (item == null)
		{
			if (traps.isEmpty() && pendingLays.isEmpty())
			{
				stop("Out of box traps");
			}
			else
			{
				status = "No box traps in inventory";
			}
			return;
		}

		WorldPoint here = me.getWorldLocation();
		if (visible.contains(here) || pendingLays.containsKey(here))
		{
			status = "Moving to a free tile";
			Polygon tile = freeNeighbourTile(here, visible);
			if (tile != null && click(randomPoint(tile)))
			{
				acted();
			}
			return;
		}

		status = "Laying trap";
		if (click(randomPoint(item)))
		{
			pendingLays.put(here, tick);
			acted();
		}
	}

	private void start()
	{
		pendingLays.clear();
		cooldown = 0;
		lastActionTick = tick;
		status = "Running";
		running = true;
		chat("Three tick hunter started");
	}

	private void stop(String reason)
	{
		running = false;
		status = reason;
		chat("Three tick hunter: " + reason);
	}

	private void acted()
	{
		lastActionTick = tick;
		cooldown = Math.max(0, config.tickDelay() - 1) + (random.nextInt(100) < config.jitterChance() ? 1 : 0);
	}

	private void chat(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
	}

	// ---- scene scanning ----

	private static class Trap
	{
		GameObject object;
		WorldPoint location;
		String[] actions;

		boolean has(String action)
		{
			return Arrays.stream(actions).anyMatch(a -> a != null && a.equalsIgnoreCase(action));
		}
	}

	private List<Trap> findTraps(Player me)
	{
		Set<String> names = Arrays.stream(config.trapNames().split(","))
			.map(s -> s.trim().toLowerCase(Locale.ROOT))
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toSet());

		List<Trap> result = new ArrayList<>();
		Scene scene = client.getScene();
		Tile[][][] tiles = scene.getTiles();
		WorldPoint wp = me.getWorldLocation();
		int plane = wp.getPlane();
		LocalPoint lp = me.getLocalLocation();
		int r = config.radius();

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
					if (def.getImpostorIds() != null)
					{
						ObjectComposition impostor = def.getImpostor();
						if (impostor != null)
						{
							def = impostor;
						}
					}
					if (def.getName() == null || !names.contains(def.getName().toLowerCase(Locale.ROOT)))
					{
						continue;
					}
					Trap trap = new Trap();
					trap.object = obj;
					trap.location = obj.getWorldLocation();
					trap.actions = def.getActions();
					result.add(trap);
				}
			}
		}
		return result;
	}

	private Trap nearest(List<Trap> traps, String action, Player me)
	{
		WorldPoint here = me.getWorldLocation();
		Trap best = null;
		for (Trap t : traps)
		{
			if (t.has(action) && t.object.getClickbox() != null
				&& (best == null || t.location.distanceTo2D(here) < best.location.distanceTo2D(here)))
			{
				best = t;
			}
		}
		return best;
	}

	private Polygon freeNeighbourTile(WorldPoint here, Set<WorldPoint> occupied)
	{
		List<int[]> offsets = new ArrayList<>(Arrays.asList(
			new int[]{1, 0}, new int[]{-1, 0}, new int[]{0, 1}, new int[]{0, -1},
			new int[]{1, 1}, new int[]{-1, 1}, new int[]{1, -1}, new int[]{-1, -1}));
		Collections.shuffle(offsets, random);

		CollisionData[] maps = client.getCollisionMaps();
		for (int[] o : offsets)
		{
			WorldPoint target = here.dx(o[0]).dy(o[1]);
			if (occupied.contains(target) || pendingLays.containsKey(target))
			{
				continue;
			}
			LocalPoint local = LocalPoint.fromWorld(client, target);
			if (local == null)
			{
				continue;
			}
			if (maps != null && maps[here.getPlane()] != null)
			{
				int flags = maps[here.getPlane()].getFlags()[local.getSceneX()][local.getSceneY()];
				if ((flags & (CollisionDataFlag.BLOCK_MOVEMENT_FULL
					| CollisionDataFlag.BLOCK_MOVEMENT_OBJECT
					| CollisionDataFlag.BLOCK_MOVEMENT_FLOOR)) != 0)
				{
					continue;
				}
			}
			Polygon poly = Perspective.getCanvasTilePoly(client, local);
			if (poly != null)
			{
				return poly;
			}
		}
		return null;
	}

	// ---- inventory ----

	private Rectangle findInventoryItem(Widget inventory, int itemId)
	{
		Widget[] children = inventory.getDynamicChildren();
		if (children == null)
		{
			return null;
		}
		for (Widget w : children)
		{
			if (w != null && !w.isHidden() && w.getItemId() == itemId)
			{
				return w.getBounds();
			}
		}
		return null;
	}

	private int usedSlots(Widget inventory)
	{
		Widget[] children = inventory.getDynamicChildren();
		if (children == null)
		{
			return 0;
		}
		int used = 0;
		for (Widget w : children)
		{
			if (w != null && w.getItemId() > 0 && w.getItemQuantity() > 0)
			{
				used++;
			}
		}
		return used;
	}

	// ---- mouse ----

	private Point randomPoint(Shape shape)
	{
		Rectangle b = shape.getBounds();
		for (int i = 0; i < 10; i++)
		{
			int x = (int) (b.getCenterX() + random.nextGaussian() * b.width / 6.0);
			int y = (int) (b.getCenterY() + random.nextGaussian() * b.height / 6.0);
			if (shape.contains(x, y))
			{
				return new Point(x, y);
			}
		}
		return new Point((int) b.getCenterX(), (int) b.getCenterY());
	}

	private boolean clickShape(Shape shape)
	{
		return shape != null && click(randomPoint(shape));
	}

	private boolean click(Point p)
	{
		Canvas canvas = client.getCanvas();
		if (canvas == null || p == null || p.x < 0 || p.y < 0 || p.x >= canvas.getWidth() || p.y >= canvas.getHeight())
		{
			return false;
		}
		mouse.submit(() ->
		{
			try
			{
				send(canvas, MouseEvent.MOUSE_MOVED, p, 0, 0, 0);
				Thread.sleep(30 + random.nextInt(50));
				send(canvas, MouseEvent.MOUSE_PRESSED, p, MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON1, 1);
				Thread.sleep(25 + random.nextInt(40));
				send(canvas, MouseEvent.MOUSE_RELEASED, p, 0, MouseEvent.BUTTON1, 1);
				send(canvas, MouseEvent.MOUSE_CLICKED, p, 0, MouseEvent.BUTTON1, 1);
			}
			catch (InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
		});
		return true;
	}

	private void send(Canvas canvas, int id, Point p, int modifiers, int button, int clicks)
	{
		canvas.dispatchEvent(new MouseEvent(canvas, id, System.currentTimeMillis(), modifiers, p.x, p.y, clicks, false, button));
	}
}
