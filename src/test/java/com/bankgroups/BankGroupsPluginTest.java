package com.bankgroups;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Not a real unit test - this is the standard RuneLite dev-loop entry point.
 * Right-click this file in IntelliJ and choose "Run" (make sure the run
 * configuration's VM options include -ea) to boot the actual RuneLite
 * client with Bank Groups already loaded, so you can test against a live
 * bank.
 */
public class BankGroupsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BankGroupsPlugin.class);
		RuneLite.main(args);
	}
}