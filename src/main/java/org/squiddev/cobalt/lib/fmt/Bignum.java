/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 sir-maniac
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.squiddev.cobalt.lib.fmt;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * A mutable proxy object to java BigDecimal.  Probably can be
 *   optimized away at some point, but for now it will get the things into a functional state.
 */
public class Bignum {

	private BigDecimal val;

	public Bignum() {
		val = new BigDecimal(0);
	}

	public void AssignUInt(short value) {
		val = BigDecimal.valueOf(Short.toUnsignedInt(value) );
	}

	public void AssignUInt(int value) {
		val = BigDecimal.valueOf(Integer.toUnsignedLong(value) );
	}

	public void AssignUInt64(long value) {
		val = fromUnsigned(value);
	}
	public void AssignBignum(Bignum other) {
		this.val = other.val;
	}

	public void AssignDecimalString(char[] value) {
		this.val = new BigDecimal(value);
	}
	public void AssignHexString(char[] value) {
		this.val = new BigDecimal(new BigInteger(String.valueOf(value), 16));
	}

	public void AssignPower(int base, int exponent) {
		val = BigDecimal.valueOf(base, exponent);
	}

	public void AddUInt64(long operand) {
		val = val.add(fromUnsigned(operand));
	}

	public void AddBignum(Bignum other) {
		val = val.add(other.val);
	}

	// Precondition: this >= other.
	public void SubtractBignum(Bignum other) {
		val = val.subtract(other.val);
	}

	void Square() {
		val = val.multiply(val);
	}

	void ShiftLeft(int shift_amount) {
		val = new BigDecimal(val.toBigIntegerExact().shiftLeft(shift_amount));
	}

	void MultiplyByUInt32(int factor) {
		val = val.multiply(fromUnsigned(factor));
	}

	void MultiplyByUInt64(long factor) {
		val = val.multiply(fromUnsigned(factor));
	}

//	void MultiplyByPowerOfTen(int exponent);

	void Times10() { MultiplyByUInt32(10); }

	// Pseudocode:
	//  int result = this / other;
	//  this = this % other;
	// In the worst case this function is in O(this/other).
	int DivideModuloIntBignum(Bignum other) {
		int quotient = val.divide(other.val).intValueExact();
		val = val.remainder(other.val);
		return quotient;
	}

//	bool ToHexString(char* buffer, const int buffer_size) const;

	// Returns
	//  -1 if a < b,
	//   0 if a == b, and
	//  +1 if a > b.
	static int Compare(Bignum a, Bignum b) {
		return a.val.compareTo(b.val);
	}

	static boolean Equal(Bignum a, Bignum b) {
		return Compare(a, b) == 0;
	}
	static boolean LessEqual(Bignum a, Bignum b) {
		return Compare(a, b) <= 0;
	}
	static boolean Less(Bignum a, Bignum b) {
		return Compare(a, b) < 0;
	}
	// Returns Compare(a + b, c);
	static int PlusCompare(Bignum a, Bignum b, Bignum c) {
		return a.val.add(b.val).compareTo(c.val);
	}
	// Returns a + b == c
	static boolean PlusEqual(Bignum a, Bignum b, Bignum c) {
		return PlusCompare(a, b, c) == 0;
	}
	// Returns a + b <= c
	static boolean PlusLessEqual(Bignum a, Bignum b, Bignum c) {
		return PlusCompare(a, b, c) <= 0;
	}
	// Returns a + b < c
	static boolean PlusLess(Bignum a, Bignum b, Bignum c) {
		return PlusCompare(a, b, c) < 0;
	}


	private static BigDecimal fromUnsigned(short value) {
		return new BigDecimal(BigInteger.valueOf(Short.toUnsignedLong(value)));
	}

	private static BigDecimal fromUnsigned(int value) {
		return new BigDecimal(BigInteger.valueOf(Integer.toUnsignedLong(value)));
	}

	private static BigDecimal fromUnsigned(long value) {
		if (value < 0) {
			return new BigDecimal(BigInteger.valueOf(value & Long.MAX_VALUE).setBit(Long.SIZE-1));
		} else {
			return BigDecimal.valueOf(value);
		}
	}
}
