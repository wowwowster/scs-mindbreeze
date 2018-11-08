package com.sword.gsa.spis.gsp;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.sword.gsa.spis.scs.push.connector.threading.ExploringTask;
import com.sword.gsa.spis.scs.push.tree.ContainerNode;
import com.sword.gsa.spis.scs.push.tree.DocumentNode;
import com.sword.gsa.spis.scs.push.tree.Node;

public class TestDuplicateRemoval {

	@SuppressWarnings("static-method")
	@Test
	public void testDuplicateRemoval() {
		final List<Node> nodes = new ArrayList<>();
		final ContainerNode parent = new ContainerNode("1", null);
		nodes.add(new DocumentNode("1", parent));
		nodes.add(new DocumentNode("2", parent));
		nodes.add(new DocumentNode("2", parent));
		nodes.add(new DocumentNode("3", parent));
		nodes.add(new ContainerNode("4", parent));
		nodes.add(new DocumentNode("4", parent));
		nodes.add(new DocumentNode("4", parent));
		nodes.add(new DocumentNode("5", parent));
		nodes.add(new DocumentNode("6", parent));
		nodes.add(new DocumentNode("6", parent));
		nodes.add(new DocumentNode("7", parent));
		System.out.println("List before: " + nodes);
		ExploringTask.ensureNoDuplicates(nodes);
		System.out.println("List after: " + nodes);
		Assert.assertEquals(8, nodes.size());
	}

}
