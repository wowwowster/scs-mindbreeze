package com.sword.tests;

import java.net.InetAddress;
import java.net.Socket;


public class PortBindTest {
	
	public static void main(String[] args) {
		
		try (Socket s = new Socket(InetAddress.getLocalHost(), 37080)) {
			System.out.println("OK");
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
	}

}
