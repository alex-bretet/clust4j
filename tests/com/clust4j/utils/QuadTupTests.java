package com.clust4j.utils;

import static org.junit.Assert.*;

import org.junit.Test;

public class QuadTupTests {

	@Test
	public void testQuads() {
		QuadTup<Integer, Integer, Integer, Integer> qt =
			new QuadTup<>(1,2,null,4);
		assertNotNull(qt.toString());
	}
	
	@Test
	public void testTris() {
		TriTup<Integer, Integer, Integer> tt =
			new TriTup<>(1,null,2);
		assertNotNull(tt.toString());
	}

	@Test
	public void testEntryPairs() {
		EntryPair<Integer, Integer> ep = new EntryPair<>(1,2);
		assertTrue(ep.setValue(3) == 2);
		assertNotNull(ep.toString());
	}
}