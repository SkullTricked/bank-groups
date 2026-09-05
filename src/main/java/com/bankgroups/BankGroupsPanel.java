package com.bankgroups;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Side panel UI. One "Edit mode" toggle at the top, then one row per group:
 * a radio button to make it the active group, an editable name field, and a
 * colour swatch button.
 */
public class BankGroupsPanel extends PluginPanel
{
	private final BankGroupsPlugin plugin;
	private final JButton editModeButton = new JButton();

	BankGroupsPanel(BankGroupsPlugin plugin)
	{
		super(false);
		this.plugin = plugin;

		// Attached once, here, and never again: buildEditModeButton() is
		// called again on every rebuild() (e.g. after each in-game toggle)
		// but it reuses this same button instance, so adding the listener
		// there too would stack up duplicate listeners over time.
		editModeButton.addActionListener(e ->
		{
			plugin.setEditMode(!plugin.isEditMode());
			refreshEditModeButton();
		});

		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		add(buildEditModeButton(), BorderLayout.NORTH);
		add(buildGroupList(), BorderLayout.CENTER);

		refreshEditModeButton();
	}

	private JPanel buildEditModeButton()
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.add(editModeButton, BorderLayout.CENTER);
		return wrapper;
	}

	private void refreshEditModeButton()
	{
		boolean on = plugin.isEditMode();
		editModeButton.setText(on ? "Edit mode: ON" : "Edit mode: OFF");
		editModeButton.setBackground(on ? new Color(60, 140, 60) : ColorScheme.DARKER_GRAY_COLOR);
		editModeButton.setForeground(Color.WHITE);
	}

	private JPanel buildGroupList()
	{
		JPanel list = new JPanel();
		list.setLayout(new GridLayout(0, 1, 0, 6));

		ButtonGroup radioGroup = new ButtonGroup();

		// "None" clears the active group so clicks in edit mode do nothing
		JRadioButton none = new JRadioButton("No active group");
		none.setSelected(plugin.getActiveGroupId() < 0);
		none.addActionListener(e -> plugin.setActiveGroupId(-1));
		radioGroup.add(none);
		list.add(none);

		for (GroupData group : plugin.getGroups())
		{
			list.add(buildGroupRow(group, radioGroup));
		}

		return list;
	}

	private JPanel buildGroupRow(GroupData group, ButtonGroup radioGroup)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

		JRadioButton select = new JRadioButton();
		select.setSelected(plugin.getActiveGroupId() == group.getId());
		select.addActionListener(e -> plugin.setActiveGroupId(group.getId()));
		radioGroup.add(select);

		JTextField name = new JTextField(group.getName());
		name.getDocument().addDocumentListener(new DocumentListener()
		{
			private void update()
			{
				group.setName(name.getText());
				plugin.saveGroups();
			}

			@Override
			public void insertUpdate(DocumentEvent e)
			{
				update();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				update();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				update();
			}
		});

		JButton swatch = new JButton();
		swatch.setPreferredSize(new Dimension(22, 22));
		swatch.setBackground(group.getColor());
		swatch.setOpaque(true);
		swatch.setBorderPainted(false);
		swatch.addActionListener(e ->
		{
			Color chosen = JColorChooser.showDialog(this, "Choose colour for " + group.getName(), group.getColor());
			if (chosen != null)
			{
				group.setColor(chosen);
				swatch.setBackground(chosen);
				plugin.saveGroups();
			}
		});

		JLabel countLabel = new JLabel(String.valueOf(group.getItemIds().size()));
		countLabel.setHorizontalAlignment(SwingConstants.CENTER);
		countLabel.setForeground(Color.LIGHT_GRAY);
		countLabel.setPreferredSize(new Dimension(24, 22));

		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		left.add(select);
		left.add(swatch);

		row.add(left, BorderLayout.WEST);
		row.add(name, BorderLayout.CENTER);
		row.add(countLabel, BorderLayout.EAST);

		return row;
	}

	/**
	 * Refreshes item counts / selection state, e.g. after a click in the bank toggles a group's contents.
	 */
	void rebuild()
	{
		removeAll();
		add(buildEditModeButton(), BorderLayout.NORTH);
		add(buildGroupList(), BorderLayout.CENTER);
		refreshEditModeButton();
		revalidate();
		repaint();
	}
}