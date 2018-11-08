package com.sword.gsa.spis.gsp;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class TestDeadSocket {

	@SuppressWarnings("static-method")
	@Test
	public void testDeadSocket() {

		try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), 25678); OutputStream socketOs = socket.getOutputStream(); InputStream socketIs = socket.getInputStream()) {
			socketOs.write("GET /ping.action HTTP/1.0 \r\n\r\n".getBytes(StandardCharsets.UTF_8));
			socketOs.flush();
			final byte[] buf = new byte[4096];
			final int r = socketIs.read(buf);
			final String resp = new String(buf, 0, r, StandardCharsets.UTF_8);
			System.out.println("Connector answered ping request with " + resp);
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

}
