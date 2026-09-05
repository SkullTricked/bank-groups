package com.bankgroups;

import java.awt.Color;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A single highlight group: a name, a colour, and the set of canonical
 * (placeholder-resolved) item IDs that belong to it.
 * <p>
 * Instances of this class are serialized to JSON via Gson and stored per
 * RuneLite profile through {@link BankGroupsPlugin}, so field names matter
 * for (de)serialization - don't rename them without a migration plan.
 */
public class GroupData
{
	private final int id;
	private String name;
	private int colorRgb;
	private final Set<Integer> itemIds = new LinkedHashSet<>();

	public GroupData(int id, String name, Color color)
	{
		this.id = id;
		this.name = name;
		this.colorRgb = color.getRGB();
	}

	public int getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public Color getColor()
	{
		return new Color(colorRgb, true);
	}

	public void setColor(Color color)
	{
		this.colorRgb = color.getRGB();
	}

	public Set<Integer> getItemIds()
	{
		return itemIds;
	}

	public boolean contains(int canonicalItemId)
	{
		return itemIds.contains(canonicalItemId);
	}

	/**
	 * @return true if the item was added, false if it was removed (it was already present)
	 */
	public boolean toggle(int canonicalItemId)
	{
		if (itemIds.remove(canonicalItemId))
		{
			return false;
		}
		itemIds.add(canonicalItemId);
		return true;
	}

	public void remove(int canonicalItemId)
	{
		itemIds.remove(canonicalItemId);
	}
}
