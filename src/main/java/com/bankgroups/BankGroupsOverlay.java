package com.bankgroups;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws a single merged outline per group across the whole bank grid, rather
 * than a box per item: adjacent same-group cells share an edge with no
 * border drawn between them, so a block of items reads as one Tetris-style
 * piece instead of many separate boxes.
 * <p>
 * Row/column position for each item is derived from its ABSOLUTE on-screen
 * position divided by the grid pitch (item spacing) - not by counting which
 * items happen to be present in a row. That distinction matters: if a row
 * has an empty slot in the middle, counting-based indexing silently
 * compresses everything after the gap and desyncs adjacency right at
 * irregular shapes. Position-based indexing doesn't have that problem.
 */
public class BankGroupsOverlay extends Overlay
{
	private final Client client;
	private final BankGroupsPlugin plugin;
	private final ItemManager itemManager;
	private final BankGroupsConfig config;

	@Inject
	private BankGroupsOverlay(Client client, BankGroupsPlugin plugin, ItemManager itemManager, BankGroupsConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.config = config;
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPosition(OverlayPosition.DYNAMIC);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (container == null || container.isHidden())
		{
			return null;
		}

		Widget[] children = container.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			return null;
		}

		List<RawItem> raw = new ArrayList<>();
		TreeSet<Integer> xs = new TreeSet<>();
		TreeSet<Integer> ys = new TreeSet<>();

		for (Widget child : children)
		{
			if (child == null || child.isHidden())
			{
				continue;
			}

			int itemId = child.getItemId();
			if (itemId <= 0)
			{
				continue;
			}

			Rectangle bounds = child.getBounds();
			if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
			{
				continue;
			}

			raw.add(new RawItem(bounds, itemId));
			xs.add(bounds.x);
			ys.add(bounds.y);
		}

		if (raw.isEmpty())
		{
			return null;
		}

		int pitchX = commonPitch(xs, raw.get(0).bounds.width);
		int pitchY = commonPitch(ys, raw.get(0).bounds.height);

		List<Cell> cells = new ArrayList<>();
		for (RawItem r : raw)
		{
			// Resolve placeholders to their real item so a group matches an
			// item whether it's physically present or just a placeholder.
			int canonicalId = itemManager.canonicalize(r.itemId);
			GroupData group = plugin.getGroupForItem(canonicalId);
			if (group == null)
			{
				continue;
			}

			// Grid position (for adjacency only, never for pixel placement)
			// from absolute pixel position, not from counting items present
			// in this row/column.
			int col = Math.round(r.bounds.x / (float) pitchX);
			int row = Math.round(r.bounds.y / (float) pitchY);

			// The drawn rectangle is anchored to THIS item's own real
			// on-screen position, not extrapolated from some other
			// reference item. Extrapolating from a single reference point
			// (an earlier version of this code did that) multiplies any
			// tiny pitch measurement error by how far a row is from the
			// reference - invisible near the top of the bank, but growing
			// steadily worse toward the bottom and while scrolling. Using
			// each item's own bounds directly has no such drift: it's
			// always anchored to where that item actually is.
			int rawX = r.bounds.x + config.horizontalOffset();
			int rawY = r.bounds.y - config.topClearance() + config.verticalOffset();
			int rawW = pitchX;
			int rawH = pitchY + config.topClearance();

			int pad = config.cellPadding();
			Rectangle tile = new Rectangle(rawX + pad, rawY + pad, rawW - pad * 2, rawH - pad * 2);

			cells.add(new Cell(tile, group, row, col));
		}

		if (cells.isEmpty())
		{
			return null;
		}

		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			// Clip to the bank's visible viewport so scrolled-off rows don't
			// paint outside the panel.
			Rectangle clip = container.getBounds();
			if (clip != null)
			{
				g.setClip(clip);
			}

			// Every shape here is an axis-aligned rectangle/line - antialiasing
			// only introduces sub-pixel blending seams at shared edges between
			// differently-coloured tiles, so it stays off.
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

			// Fill every cell first so the border strokes drawn afterwards
			// sit cleanly on top of the fill rather than under it.
			for (Cell cell : cells)
			{
				Color base = cell.group.getColor();
				g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), config.fillOpacity()));
				g.fill(cell.bounds);
			}

			g.setStroke(new BasicStroke(config.borderWidth()));

			for (Cell cell : cells)
			{
				g.setColor(cell.group.getColor());

				Rectangle b = cell.bounds;
				boolean hasTop = hasNeighbor(cells, cell, 0, -1);
				boolean hasBottom = hasNeighbor(cells, cell, 0, 1);
				boolean hasLeft = hasNeighbor(cells, cell, -1, 0);
				boolean hasRight = hasNeighbor(cells, cell, 1, 0);

				if (!hasTop)
				{
					g.drawLine(b.x, b.y, b.x + b.width, b.y);
				}
				if (!hasBottom)
				{
					g.drawLine(b.x, b.y + b.height, b.x + b.width, b.y + b.height);
				}
				if (!hasLeft)
				{
					g.drawLine(b.x, b.y, b.x, b.y + b.height);
				}
				if (!hasRight)
				{
					g.drawLine(b.x + b.width, b.y, b.x + b.width, b.y + b.height);
				}
			}
		}
		finally
		{
			g.dispose();
		}

		return null;
	}

	/**
	 * @return the most common positive gap between consecutive values in a
	 * sorted set of distinct pixel positions - i.e. the true single-cell
	 * spacing. Using the most common gap (rather than the smallest one) means
	 * a single anomalous pair anywhere on screen can't throw off the pitch
	 * for the entire bank.
	 */
	private int commonPitch(TreeSet<Integer> sortedDistinct, int fallback)
	{
		Map<Integer, Integer> frequency = new HashMap<>();
		Integer previous = null;
		for (int value : sortedDistinct)
		{
			if (previous != null)
			{
				int diff = value - previous;
				if (diff > 0)
				{
					frequency.merge(diff, 1, Integer::sum);
				}
			}
			previous = value;
		}

		int best = fallback;
		int bestCount = 0;
		for (Map.Entry<Integer, Integer> entry : frequency.entrySet())
		{
			if (entry.getValue() > bestCount)
			{
				bestCount = entry.getValue();
				best = entry.getKey();
			}
		}
		return best;
	}

	/**
	 * @param dx -1 = check the cell to the left, 1 = check the cell to the right, 0 = don't check horizontally
	 * @param dy -1 = check the cell above, 1 = check the cell below, 0 = don't check vertically
	 * @return true if another cell in the same group sits directly adjacent in that direction
	 */
	private boolean hasNeighbor(List<Cell> cells, Cell cell, int dx, int dy)
	{
		int targetRow = cell.row + dy;
		int targetCol = cell.col + dx;

		for (Cell other : cells)
		{
			if (other == cell || other.group.getId() != cell.group.getId())
			{
				continue;
			}

			if (other.row == targetRow && other.col == targetCol)
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * One item widget with its real screen bounds, not yet filtered by group
	 * membership or converted to grid coordinates.
	 */
	private static class RawItem
	{
		final Rectangle bounds;
		final int itemId;

		RawItem(Rectangle bounds, int itemId)
		{
			this.bounds = bounds;
			this.itemId = itemId;
		}
	}

	private static class Cell
	{
		final Rectangle bounds;
		final GroupData group;
		final int row;
		final int col;

		Cell(Rectangle bounds, GroupData group, int row, int col)
		{
			this.bounds = bounds;
			this.group = group;
			this.row = row;
			this.col = col;
		}
	}
}