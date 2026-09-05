package com.bankgroups;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

@PluginDescriptor(
		name = "Bank Groups",
		description = "Highlight bank items into up to 10 colour-coded, named groups (e.g. melee gear, mage gear, potions)",
		tags = {"bank", "highlight", "organization", "groups"}
)
public class BankGroupsPlugin extends Plugin
{
	static final String CONFIG_GROUP = "bankgroups";
	private static final String CONFIG_KEY_GROUPS = "groupdata";
	private static final int GROUP_COUNT = 10;

	// Reasonably distinct default palette for the 10 starter groups.
	private static final Color[] DEFAULT_COLORS = {
			new Color(230, 60, 60),   // red
			new Color(60, 140, 230),  // blue
			new Color(60, 200, 90),   // green
			new Color(230, 190, 40),  // yellow
			new Color(190, 90, 230),  // purple
			new Color(240, 140, 30),  // orange
			new Color(40, 210, 210),  // teal
			new Color(230, 100, 170), // pink
			new Color(150, 150, 150), // grey
			new Color(120, 200, 40)   // lime
	};

	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Inject
	private BankGroupsOverlay overlay;

	@Inject
	private BankGroupsConfig config;

	private BankGroupsPanel panel;
	private NavigationButton navButton;

	private final List<GroupData> groups = new ArrayList<>();
	private boolean editMode = false;
	private int activeGroupId = -1;

	@Provides
	BankGroupsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BankGroupsConfig.class);
	}

	@Override
	protected void startUp()
	{
		loadGroups();

		overlayManager.add(overlay);

		panel = new BankGroupsPanel(this);
		BufferedImage icon = createIcon();
		navButton = NavigationButton.builder()
				.tooltip("Bank Groups")
				.icon(icon)
				.priority(6)
				.panel(panel)
				.build();
		clientToolbar.addNavigation(navButton);
	}

	/**
	 * Draws a simple chest-with-dot icon in code, so the plugin doesn't
	 * depend on a bundled image resource that might not have made it into
	 * the build.
	 */
	private BufferedImage createIcon()
	{
		BufferedImage img = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

			g.setColor(new Color(120, 95, 55));
			g.fillRoundRect(1, 8, 22, 14, 4, 4);
			g.setColor(new Color(90, 70, 40));
			g.fillRoundRect(1, 3, 22, 8, 4, 4);

			g.setColor(new Color(20, 15, 10));
			g.drawRoundRect(1, 3, 21, 19, 4, 4);

			g.setColor(new Color(230, 60, 60));
			g.fillOval(9, 12, 6, 6);
		}
		finally
		{
			g.dispose();
		}
		return img;
	}

	@Override
	protected void shutDown()
	{
		saveGroups();
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		editMode = false;
		activeGroupId = -1;
	}

	// ---------------------------------------------------------------
	// Edit mode / bank click interception
	// ---------------------------------------------------------------

	@Subscribe
	public void onClientTick(ClientTick tick)
	{
		if (!editMode)
		{
			return;
		}

		Widget container = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
		if (container == null || container.isHidden())
		{
			return;
		}

		MenuEntry[] entries = client.getMenuEntries();
		if (entries.length == 0)
		{
			return;
		}

		// Strip out every default action on this widget - Withdraw-1/5/10/X/All,
		// Examine, Placeholder, everything. This is deliberately aggressive:
		// leaving even one original entry in place risks it triggering special
		// behaviour (e.g. the numeric quantity prompt "Withdraw-X" opens for
		// stackable items like coins) that our click handler can't cleanly
		// intercept. Nothing from the bank's own menu survives while editing.
		List<MenuEntry> kept = new ArrayList<>();
		MenuEntry sourceEntry = null;
		for (MenuEntry entry : entries)
		{
			if (entry.getParam1() == WidgetInfo.BANK_ITEM_CONTAINER.getId())
			{
				if (sourceEntry == null)
				{
					sourceEntry = entry;
				}
				continue;
			}
			kept.add(entry);
		}

		if (sourceEntry == null)
		{
			// Nothing on this widget - leave the rest of the menu untouched.
			return;
		}

		if (activeGroupId >= 0)
		{
			int itemId = resolveItemId(sourceEntry.getParam0(), sourceEntry.getParam1());
			GroupData active = getGroupById(activeGroupId);
			if (itemId > 0 && active != null)
			{
				int canonicalId = itemManager.canonicalize(itemId);
				String label = active.contains(canonicalId) ? "Remove from " : "Add to ";

				MenuEntry custom = client.createMenuEntry(-1)
						.setOption(label + active.getName())
						.setTarget(sourceEntry.getTarget())
						.setType(MenuAction.RUNELITE)
						.setParam0(sourceEntry.getParam0())
						.setParam1(sourceEntry.getParam1())
						.setIdentifier(sourceEntry.getIdentifier());
				kept.add(custom);
			}
		}

		client.setMenuEntries(kept.toArray(new MenuEntry[0]));
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!editMode)
		{
			return;
		}

		if (!isBankItemContainer(event.getParam1()))
		{
			return;
		}

		// Consume unconditionally: while in edit mode, nothing in the bank item
		// container should withdraw, examine-and-close, or otherwise act normally.
		event.consume();

		if (activeGroupId < 0)
		{
			return;
		}

		int itemId = resolveItemId(event.getParam0(), event.getParam1());
		if (itemId <= 0)
		{
			return;
		}

		int canonicalId = itemManager.canonicalize(itemId);
		toggleItemInActiveGroup(canonicalId);
	}

	private boolean isBankItemContainer(int widgetId)
	{
		return widgetId == WidgetInfo.BANK_ITEM_CONTAINER.getId();
	}

	/**
	 * Resolves the item ID under a bank-item menu action. actionParam0 /
	 * getParam0() is the dynamic child index within the bank item container
	 * for widget-sourced menu entries.
	 */
	private int resolveItemId(int childIndex, int widgetId)
	{
		Widget container = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER);
		if (container == null)
		{
			return -1;
		}

		Widget[] children = container.getDynamicChildren();
		if (children == null || childIndex < 0 || childIndex >= children.length)
		{
			return -1;
		}

		Widget itemWidget = children[childIndex];
		return itemWidget == null ? -1 : itemWidget.getItemId();
	}

	// ---------------------------------------------------------------
	// Group management
	// ---------------------------------------------------------------

	private void toggleItemInActiveGroup(int canonicalId)
	{
		GroupData active = getGroupById(activeGroupId);
		if (active == null)
		{
			return;
		}

		// Groups are mutually exclusive: an item leaving another group before
		// joining this one, so borders never overlap.
		for (GroupData other : groups)
		{
			if (other.getId() != active.getId())
			{
				other.remove(canonicalId);
			}
		}

		active.toggle(canonicalId);
		saveGroups();

		if (panel != null)
		{
			panel.rebuild();
		}
	}

	/**
	 * @return the group (if any) that a canonical item ID belongs to.
	 */
	public GroupData getGroupForItem(int canonicalId)
	{
		for (GroupData group : groups)
		{
			if (group.contains(canonicalId))
			{
				return group;
			}
		}
		return null;
	}

	public GroupData getGroupById(int id)
	{
		for (GroupData group : groups)
		{
			if (group.getId() == id)
			{
				return group;
			}
		}
		return null;
	}

	public List<GroupData> getGroups()
	{
		return groups;
	}

	public boolean isEditMode()
	{
		return editMode;
	}

	public void setEditMode(boolean editMode)
	{
		this.editMode = editMode;
		if (!editMode)
		{
			activeGroupId = -1;
		}
	}

	public int getActiveGroupId()
	{
		return activeGroupId;
	}

	public void setActiveGroupId(int activeGroupId)
	{
		this.activeGroupId = activeGroupId;
	}

	// ---------------------------------------------------------------
	// Persistence (per RuneLite profile)
	// ---------------------------------------------------------------

	void saveGroups()
	{
		String json = gson.toJson(groups);
		configManager.setRSProfileConfiguration(CONFIG_GROUP, CONFIG_KEY_GROUPS, json);
	}

	private void loadGroups()
	{
		groups.clear();

		String json = configManager.getRSProfileConfiguration(CONFIG_GROUP, CONFIG_KEY_GROUPS);
		if (json != null)
		{
			Type type = new TypeToken<ArrayList<GroupData>>()
			{
			}.getType();
			List<GroupData> loaded = gson.fromJson(json, type);
			if (loaded != null && !loaded.isEmpty())
			{
				groups.addAll(loaded);
			}
		}

		if (groups.isEmpty())
		{
			for (int i = 0; i < GROUP_COUNT; i++)
			{
				groups.add(new GroupData(i, "Group " + (i + 1), DEFAULT_COLORS[i % DEFAULT_COLORS.length]));
			}
		}
	}
}