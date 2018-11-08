package com.sword.gsa.spis.gsp;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

public class TestAtomics {

	@SuppressWarnings("static-method")
	@Test
	public void testAtomics() {

		{
			final AtomicInteger ai = new AtomicInteger(0);
			Assert.assertEquals(0, ai.get());
			ai.incrementAndGet();
			Assert.assertEquals(1, ai.get());
			ai.addAndGet(6);
			Assert.assertEquals(7, ai.get());
		}

		{
			final AtomicInteger ai2 = new AtomicInteger(0);
			ai2.set(25);
			Assert.assertEquals(25, ai2.get());
			ai2.incrementAndGet();
			Assert.assertEquals(26, ai2.get());
			ai2.addAndGet(6);
			Assert.assertEquals(32, ai2.get());
		}

	}

}
