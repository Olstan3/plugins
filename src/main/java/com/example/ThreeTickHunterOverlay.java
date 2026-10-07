package com.example;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class ThreeTickHunterOverlay extends OverlayPanel
{
	private final ThreeTickHunterPlugin plugin;

	@Inject
	private ThreeTickHunterOverlay(ThreeTickHunterPlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("3-tick hunter")
			.color(plugin.isRunning() ? Color.GREEN : Color.RED)
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Status")
			.right(plugin.getStatus())
			.build());
		return super.render(graphics);
	}
}
