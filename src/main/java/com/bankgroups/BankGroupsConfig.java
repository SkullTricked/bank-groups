package com.bankgroups;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

/**
 * Visual tuning knobs. The actual group definitions (names/colours/item
 * membership) are NOT exposed here - they're managed from the side panel
 * and persisted separately per-profile, since they're a dynamic list rather
 * than fixed settings.
 */
@ConfigGroup(BankGroupsPlugin.CONFIG_GROUP)
public interface BankGroupsConfig extends Config
{
	@Range(min = 0, max = 255)
	@ConfigItem(
			keyName = "fillOpacity",
			name = "Fill opacity",
			description = "Transparency of the highlight fill inside the border (0 = invisible, 255 = solid)",
			position = 0
	)
	default int fillOpacity()
	{
		return 36;
	}

	@Range(min = 1, max = 4)
	@ConfigItem(
			keyName = "borderWidth",
			name = "Border width",
			description = "Thickness in pixels of the group border outline",
			position = 1
	)
	default int borderWidth()
	{
		return 2;
	}

	@Range(min = -15, max = 15)
	@ConfigItem(
			keyName = "horizontalOffset",
			name = "Horizontal offset",
			description = "Shifts the highlight left (negative) or right (positive) to center it between items",
			position = 2
	)
	default int horizontalOffset()
	{
		return -7;
	}

	@Range(min = 0, max = 15)
	@ConfigItem(
			keyName = "topClearance",
			name = "Top clearance",
			description = "Extra space added above each highlighted item so its quantity number stays visible",
			position = 3
	)
	default int topClearance()
	{
		return 0;
	}

	@Range(min = -15, max = 15)
	@ConfigItem(
			keyName = "verticalOffset",
			name = "Vertical offset",
			description = "Shifts the highlight up (negative) or down (positive), independent of top clearance",
			position = 4
	)
	default int verticalOffset()
	{
		return 0;
	}

	@Range(min = 0, max = 10)
	@ConfigItem(
			keyName = "cellPadding",
			name = "Cell padding",
			description = "Shrinks every tile in from all sides equally - use this to leave a visible gap between differently-coloured groups",
			position = 5
	)
	default int cellPadding()
	{
		return 0;
	}
}