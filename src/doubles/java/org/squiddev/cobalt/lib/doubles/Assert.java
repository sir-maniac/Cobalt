/*
 * Copyright 2021 sir-maniac. All rights reserved.
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *    * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of Google Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.squiddev.cobalt.lib.doubles;

final public class Assert {
	private static boolean enabled = false;

	public static boolean assertEnabled() {
		return enabled;
	}

	public static void assertThat(boolean expected) {
		if (enabled && !expected) {
			throw new DoubleConversionAssertionError("Assertion failed");
		}
	}

	public static void setEnabled(boolean enabled) {
		Assert.enabled = enabled;
	}

	public static void requireState(boolean condition, String msg) {
		if (!condition) throw new IllegalStateException(msg);
	}

	public static void requireArg(boolean condition, String msg) {
		if (!condition) throw new IllegalArgumentException(msg);
	}

	/**
	 * A unique assertion error mainly so it can be distinguished in debugging
	 */
	public static class DoubleConversionAssertionError extends AssertionError {
		private static final long serialVersionUID = 1L;

		public DoubleConversionAssertionError() {
		}

		public DoubleConversionAssertionError(String message) {
			super(message, null);
		}

		public DoubleConversionAssertionError(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private Assert() {}
}
