package com.sword.gsa.spis.gsp;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.junit.Test;

public class TestSuppressedException {

	private final Logger rl;

	public TestSuppressedException() {
		rl = Logger.getRootLogger();
		rl.removeAllAppenders();
		rl.addAppender(new ConsoleAppender(new PatternLayout("%d{dd MMM yyyy HH:mm:ss,SSS} %p %t %c - %m%n")));
		rl.setLevel(Level.DEBUG);
	}

	@Test
	public void testSuppressedException() {

		try {
			_testSuppressedException();
		} catch (final Throwable e) {
			rl.error("Error: ", e);
			// Throwable[] ses = e.getSuppressed();//only applies to exceptions thrown by close method of AutoCloseable
			// for (Throwable se : ses) {
			// rl.error("Suppressed Error: ", se);
			// }
		}

	}

	private void _testSuppressedException() throws Throwable {

		Throwable t = null;
		try (AutoCloseableThatThrows a = new AutoCloseableThatThrows(rl)) {
			rl.info("_testSuppressedException - obtained AutoCloseableThatThrows object");
			a.test();
		} catch (final Throwable _t) {
			rl.info("_testSuppressedException - caught AutoCloseableThatThrows#test exception");
			t = _t;
		} finally {
			rl.info("_testSuppressedException - entered finally block");
			try {
				methodThatThrowsInFinallyBlock();
			} catch (final Throwable _t) {
				if (t == null) throw _t;
				else {
					rl.error("Finally block threw error: ", _t);
					throw t;
				}
			}
		}

	}

	private void methodThatThrowsInFinallyBlock() throws Exception {
		rl.info("methodThatThrowsInFinallyBlock");
		throw new Exception("methodThatThrowsInFinallyBlock exception");
	}

	public static class AutoCloseableThatThrows implements AutoCloseable {

		private final Logger _rl;

		public AutoCloseableThatThrows(final Logger rl) {
			_rl = rl;
			_rl.info("AutoCloseableThatThrows constructor");
		}

		public void test() throws Exception {
			_rl.info("AutoCloseableThatThrows method");
			throw new Exception("AutoCloseableThatThrows exception");
		}

		@Override
		public void close() throws Exception {
			_rl.info("AutoCloseableThatThrows close");
			throw new Exception("AutoCloseableThatThrows exception on close");
		}

	}

}
