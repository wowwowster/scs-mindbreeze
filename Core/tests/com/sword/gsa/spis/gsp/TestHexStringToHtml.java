package com.sword.gsa.spis.gsp;

import org.junit.Test;

import com.sword.gsa.spis.scs.ui.SCSConfigUI;

public class TestHexStringToHtml {

	@SuppressWarnings("static-method")
	@Test
	public void test() {

		System.out.println(SCSConfigUI.hexToBreakableHtml(""));
		System.out.println(SCSConfigUI.hexToBreakableHtml("01"));
		System.out.println(SCSConfigUI.hexToBreakableHtml("0123456789abcd"));
		System.out.println(SCSConfigUI.hexToBreakableHtml("0123456789abcdef"));
		System.out.println(SCSConfigUI.hexToBreakableHtml("0123456789abcdef01"));
		System.out.println(SCSConfigUI.hexToBreakableHtml("0123456789abcdef0123456789abcdef"));
		System.out.println(SCSConfigUI.hexToBreakableHtml("0123456789abcdef0123456789abcdef01"));

	}

}
