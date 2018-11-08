package com.sword.gsa.spis.gsp;


public class TestExitStatus {

	public static void main(String[] args) {
		
		int i = 377;
		System.out.println("436: " + (436 & i));
		System.out.println("437: " + (437 & i));
		System.out.println("438: " + (438 & i));
		System.out.println("439: " + (439 & i));
		System.out.println("440: " + (440 & i));
		System.out.println("441: " + (441 & i));
		
		System.out.println("307: " + (307 & i));
		System.out.println("308: " + (308 & i));
		System.out.println("309: " + (309 & i));
		
		i = 0b1111_1111;
		System.out.println("436: " + (436 & i));
		System.out.println("437: " + (437 & i));
		System.out.println("438: " + (438 & i));
		System.out.println("439: " + (439 & i));
		System.out.println("440: " + (440 & i));
		System.out.println("441: " + (441 & i));
		
		System.out.println("307: " + (307 & i));
		System.out.println("308: " + (308 & i));
		System.out.println("309: " + (309 & i));

	}

}
