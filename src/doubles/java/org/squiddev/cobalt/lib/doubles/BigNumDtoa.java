/*
 * Copyright 2010 the V8 project authors. All rights reserved.
 * Java Port Copyright 2021 sir-maniac. All Rights reserved.
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

import org.checkerframework.checker.signedness.qual.Unsigned;

import static org.squiddev.cobalt.lib.doubles.Assert.DOUBLE_CONVERSION_ASSERT;
import static org.squiddev.cobalt.lib.doubles.UnsignedValues.*;

public class BigNumDtoa {

	@SuppressWarnings("ImplicitNumericConversion")
	private static final int ASCII_ZERO = '0';
	@SuppressWarnings("ImplicitNumericConversion")
	private static final int ASCII_NINE = '9';

	public enum BignumDtoaMode {
		/**
		 *  Return the shortest correct representation.
		 *  For example the output of 0.299999999999999988897 is (the less accurate but
		 *  correct) 0.3.
		 */
		SHORTEST,
		/**
		 *  Return a fixed number of digits after the decimal point.
		 *  For instance fixed(0.1, 4) becomes 0.1000
		 *  If the input number is big, the output will be big.
		 */
		FIXED,
		/** Return a fixed number of digits, no matter what the exponent is. */
		PRECISION
	}

	private static int normalizedExponent(@Unsigned long significand, int exponent) {
		DOUBLE_CONVERSION_ASSERT(significand != 0L);
		while ((significand & Ieee.Double.HIDDEN_BIT) == 0L) {
			significand = significand << 1;
			exponent = exponent - 1;
		}
		return exponent;
	}

	/**
	 *  Converts the given double 'v' to ascii.
	 *  The result should be interpreted as buffer * 10^(point-length).
	 *
	 *  The input v must be > 0 and different from NaN, and Infinity.
	 *
	 *  The output depends on the given mode:
	 *   - SHORTEST: produce the least amount of digits for which the internal
	 *    identity requirement is still satisfied. If the digits are printed
	 *    (together with the correct exponent) then reading this number will give
	 *    'v' again. The buffer will choose the representation that is closest to
	 *    'v'. If there are two at the same distance, than the number is round up.
	 *    In this mode the 'requestedDigits' parameter is ignored.
	 *   - FIXED: produces digits necessary to print a given number with
	 *    'requestedDigits' digits after the decimal point. The produced digits
	 *    might be too short in which case the caller has to fill the gaps with '0's.
	 *    Example: toFixed(0.001, 5) is allowed to return buffer="1", point=-2.
	 *    Halfway cases are rounded up. The call toFixed(0.15, 2) thus returns
	 * 	 buffer="2", point=0.
	 *    Note: the length of the returned buffer has no meaning wrt the significance
	 *    of its digits. That is, just because it contains '0's does not mean that
	 *    any other digit would not satisfy the internal identity requirement.
	 *   - PRECISION: produces 'requestedDigits' where the first digit is not '0'.
	 *    Even though the length of produced digits usually equals
	 *    'requestedDigits', the function is allowed to return fewer digits, in
	 *    which case the caller has to fill the missing digits with '0's.
	 *    Halfway cases are again rounded up.
	 *  'bignumDtoa' expects the given buffer to be big enough to hold all digits.
	 */
	public static void bignumDtoa(double v, BignumDtoaMode mode, int requestedDigits,
						   DecimalRepBuf buf) {
		DOUBLE_CONVERSION_ASSERT(v > 0.0);
		DOUBLE_CONVERSION_ASSERT(!new Ieee.Double(v).isSpecial());
		long significand;
		int exponent;
		boolean lowerBoundaryIsCloser;
		significand = new Ieee.Double(v).significand();
		exponent = new Ieee.Double(v).exponent();
		lowerBoundaryIsCloser = new Ieee.Double(v).lowerBoundaryIsCloser();
		boolean needBoundaryDeltas = mode == BignumDtoaMode.SHORTEST;

		boolean isEven = (significand & 1L) == 0L;
		int normalizedExponent = normalizedExponent(significand, exponent);
		// estimatePower might be too low by 1.
		int estimatePower = estimatePower(normalizedExponent);

		// Shortcut for Fixed.
		// The requested digits correspond to the digits after the point. If the
		// number is much too small, then there is no need in trying to get any
		// digits.
		if (mode == BignumDtoaMode.FIXED && -estimatePower - 1 > requestedDigits) {
			buf.clearBuf();
			// Set decimal-point to -requestedDigits. This is what Gay does.
			// Note that it should not have any effect anyways since the string is
			// empty.
			buf.setPointPosition(-requestedDigits);
			return;
		}

		int[] estimatedPoint = new int[1];
		Bignum numerator = new Bignum();
		Bignum denominator = new Bignum();
		Bignum deltaMinus = new Bignum();
		Bignum deltaPlus = new Bignum();
		// Make sure the bignum can grow large enough. The smallest double equals
		// 4e-324. In this case the denominator needs fewer than 324*4 binary digits.
		// The maximum double is 1.7976931348623157e308 which needs fewer than
		// 308*4 binary digits.
		//DOUBLE_CONVERSION_ASSERT(Bignum.kMaxSignificantBits >= 324*4);
		initialScaledStartValues(significand, exponent, lowerBoundaryIsCloser,
				estimatePower, needBoundaryDeltas,
				numerator, denominator,
                           deltaMinus, deltaPlus);
		// We now have v = (numerator / denominator) * 10^estimatePower.
		fixupMultiply10(estimatePower, isEven, estimatedPoint,
				numerator, denominator,
                  deltaMinus, deltaPlus);
		buf.setPointPosition(estimatedPoint[0]);
		// We now have v = (numerator / denominator) * 10^(decimalPoint-1), and
		//  1 <= (numerator + deltaPlus) / denominator < 10
		switch (mode) {
			case SHORTEST:
				generateShortestDigits(numerator, denominator,
                             deltaMinus, deltaPlus, isEven, buf);
				break;
			case FIXED:
				bignumToFixed(requestedDigits, numerator, denominator, buf);
				break;
			case PRECISION:
				generateCountedDigits(requestedDigits, numerator, denominator, buf);
				break;
			default:
				throw new IllegalStateException("Unreachable");
		}
	}

	/**
	 *  Generates digits from the left to the right and stops when the generated
	 *  digits yield the shortest decimal representation of v.
	 *
	 *  The procedure starts generating digits from the left to the right and stops
	 *  when the generated digits yield the shortest decimal representation of v. A
	 *  decimal representation of v is a number lying closer to v than to any other
	 *  double, so it converts to v when read.
	 *
	 *  This is true if d, the decimal representation, is between m- and m+, the
	 *  upper and lower boundaries. d must be strictly between them if !isEven.
	 * 		   m- := (numerator - deltaMinus) / denominator
	 * 		   m+ := (numerator + deltaPlus) / denominator
	 *
	 *  Precondition: 0 <= (numerator+deltaPlus) / denominator < 10.
	 *    If 1 <= (numerator+deltaPlus) / denominator < 10 then no leading 0 digit
	 *    will be produced. This should be the standard precondition.
	 */
	private static void generateShortestDigits(Bignum numerator, Bignum denominator,
									   Bignum deltaMinus, Bignum deltaPlus,
									   boolean isEven,
									   DecimalRepBuf buf) {
		// Small optimization: if deltaMinus and deltaPlus are the same just reuse
		// one of the two bignums.
		if (Bignum.equal(deltaMinus, deltaPlus)) {
			deltaPlus = deltaMinus;
		}
		buf.clearBuf();
		for (;;) {
			@Unsigned int digit = numerator.divideModuloIntBignum(denominator);
			// digit = numerator / denominator (integer division).
			// numerator = numerator % denominator.
			buf.append(digit);

			// Can we stop already?
			// If the remainder of the division is less than the distance to the lower
			// boundary we can stop. In this case we simply round down (discarding the
			// remainder).
			// Similarly we test if we can round up (using the upper boundary).
			boolean inDeltaRoomMinus;
			boolean inDeltaRoomPlus;
			if (isEven) {
				inDeltaRoomMinus = Bignum.lessEqual(numerator, deltaMinus);
			} else {
				inDeltaRoomMinus = Bignum.less(numerator, deltaMinus);
			}
			if (isEven) {
				inDeltaRoomPlus =
						Bignum.plusCompare(numerator, deltaPlus, denominator) >= 0;
			} else {
				inDeltaRoomPlus =
						Bignum.plusCompare(numerator, deltaPlus, denominator) > 0;
			}
			if (!inDeltaRoomMinus && !inDeltaRoomPlus) {
				// Prepare for next iteration.
				numerator.times10();
				deltaMinus.times10();
				// We optimized deltaPlus to be equal to deltaMinus (if they share the
				// same value). So don't multiply deltaPlus if they point to the same
				// object.
				if (!deltaMinus.equals(deltaPlus)) {
					deltaPlus.times10();
				}
			} else if (inDeltaRoomMinus && inDeltaRoomPlus) {
				// Let's see if 2*numerator < denominator.
				// If yes, then the next digit would be < 5 and we can round down.
				int compare = Bignum.plusCompare(numerator, numerator, denominator);
				if (compare < 0) {
					// Remaining digits are less than .5. -> Round down (== do nothing).
				} else if (compare > 0) {
					// Remaining digits are more than .5 of denominator. -> Round up.
					// Note that the last digit could not be a '9' as otherwise the whole
					// loop would have stopped earlier.
					// We still have an assert here in case the preconditions were not
					// satisfied.
					buf.incrementLastNoOverflow();
				} else {
					// Halfway case.
					// TODO(floitsch): need a way to solve half-way cases.
					//   For now let's round towards even (since this is what Gay seems to
					//   do).

					if (((int) buf.lastChar() - ASCII_ZERO) % 2 == 0) {
						// Round down => Do nothing.
					} else {
						buf.incrementLastNoOverflow();
					}
				}
				return;
			} else if (inDeltaRoomMinus) {
				// Round down (== do nothing).
				return;
			} else {  // inDeltaRoomPlus
				// Round up.
				// Note again that the last digit could not be '9' since this would have
				// stopped the loop earlier.
				// We still have an DOUBLE_CONVERSION_ASSERT here, in case the preconditions were not
				// satisfied.
				buf.incrementLastNoOverflow();
				return;
			}
		}
	}

	/**
	 *  Generates 'count' digits of numerator/denominator.
	 *  Once 'count' digits have been produced rounds the result depending on the
	 *  remainder (remainders of exactly .5 round upwards). Might update the
	 *  decimalPoint when rounding up (for example for 0.9999).
	 *
	 *  Let v = numerator / denominator < 10.
	 *  Then we generate 'count' digits of d = x.xxxxx... (without the decimal point)
	 *  from left to right. Once 'count' digits have been produced we decide wether
	 *  to round up or down. Remainders of exactly .5 round upwards. Numbers such
	 *  as 9.999999 propagate a carry all the way, and change the
	 *  exponent (decimalPoint), when rounding upwards.
	 */
	private static void generateCountedDigits(int count,
									  Bignum numerator, Bignum denominator,
									  DecimalRepBuf buf) {
		DOUBLE_CONVERSION_ASSERT(count >= 0);
		for (int i = 0; i < count - 1; ++i) {
			@Unsigned int digit = numerator.divideModuloIntBignum(denominator);
			// digit = numerator / denominator (integer division).
			// numerator = numerator % denominator.
			buf.append(digit);
			// Prepare for next iteration.
			numerator.times10();
		}
		// Generate the last digit.
		@Unsigned int digit = numerator.divideModuloIntBignum(denominator);
		buf.append(digit);
		if (Bignum.plusCompare(numerator, numerator, denominator) >= 0) {
			// Correct bad digits (in case we had a sequence of '9's). Propagate the
			// carry until we hat a non-'9' or til we reach the first digit.
			// If the first digit is reached, the decimal point is moved one to the right.
			buf.roundUp();
		}
	}

	/**
	 *  Generates 'requestedDigits' after the decimal point. It might omit
	 *  trailing '0's. If the input number is too small then no digits at all are
	 *  generated (ex.: 2 fixed digits for 0.00001).
	 *
	 *  Input verifies:  1 <= (numerator + delta) / denominator < 10.
	 */
	private static void bignumToFixed(int requestedDigits,
							  Bignum numerator, Bignum denominator,
							  DecimalRepBuf buf) {
		int decimalPoint = buf.getPointPosition();
		// Note that we have to look at more than just the requestedDigits, since
		// a number could be rounded up. Example: v=0.5 with requestedDigits=0.
		// Even though the power of v equals 0 we can't just stop here.
		if (-decimalPoint > requestedDigits) {
			// The number is definitively too small.
			// Ex: 0.001 with requestedDigits == 1.
			// Set decimal-point to -requestedDigits. This is what Gay does.
			// Note that it should not have any effect anyways since the string is
			// empty.
			buf.clearBuf();
			buf.setPointPosition(-requestedDigits);
			return;
		} else if (-decimalPoint == requestedDigits) {
			// We only need to verify if the number rounds down or up.
			// Ex: 0.04 and 0.06 with requestedDigits == 1.
			DOUBLE_CONVERSION_ASSERT(decimalPoint == -requestedDigits);
			// Initially the fraction lies in range (1, 10]. Multiply the denominator
			// by 10 so that we can compare more easily.
			denominator.times10();
			if (Bignum.plusCompare(numerator, numerator, denominator) >= 0) {
				// If the fraction is >= 0.5 then we have to include the rounded
				// digit.
				buf.clearBuf();
				buf.append(1);
				buf.setPointPosition( decimalPoint + 1 );
			} else {
				// Note that we caught most of similar cases earlier.
      			buf.clearBuf();
			}
			return;
		} else {
			// The requested digits correspond to the digits after the point.
			// The variable 'neededDigits' includes the digits before the point.
			int neededDigits = decimalPoint + requestedDigits;
			generateCountedDigits(neededDigits,
					numerator, denominator,
					buf);
		}
	}


	/**
	 *  Returns an estimation of k such that 10^(k-1) <= v < 10^k where
	 *  v = f * 2^exponent and 2^52 <= f < 2^53.
	 *  v is hence a normalized double with the given exponent. The output is an
	 *  approximation for the exponent of the decimal approximation .digits * 10^k.
	 *
	 *  The result might undershoot by 1 in which case 10^k <= v < 10^k+1.
	 *  Note: this property holds for v's upper boundary m+ too.
	 * 	10^k <= m+ < 10^k+1.
	 *    (see explanation below).
	 *
	 *  Examples:
	 *   estimatePower(0)   => 16
	 *   estimatePower(-52) => 0
	 *
	 *  Note: e >= 0 => EstimatedPower(e) > 0. No similar claim can be made for e<0.
	 */
	private static int estimatePower(int exponent) {
		// This function estimates log10 of v where v = f*2^e (with e == exponent).
		// Note that 10^floor(log10(v)) <= v, but v <= 10^ceil(log10(v)).
		// Note that f is bounded by its container size. Let p = 53 (the double's
		// significand size). Then 2^(p-1) <= f < 2^p.
		//
		// Given that log10(v) == log2(v)/log2(10) and e+(len(f)-1) is quite close
		// to log2(v) the function is simplified to (e+(len(f)-1)/log2(10)).
		// The computed number undershoots by less than 0.631 (when we compute log3
		// and not log10).
		//
		// Optimization: since we only need an approximated result this computation
		// can be performed on 64 bit integers. On x86/x64 architecture the speedup is
		// not really measurable, though.
		//
		// Since we want to avoid overshooting we decrement by 1e10 so that
		// floating-point imprecisions don't affect us.
		//
		// Explanation for v's boundary m+: the computation takes advantage of
		// the fact that 2^(p-1) <= f < 2^p. Boundaries still satisfy this requirement
		// (even for denormals where the delta can be much more important).

  		final double k1Log10 = 0.30102999566398114;  // 1/lg(10)

		// For doubles len(f) == 53 (don't forget the hidden bit).
		double estimate = Math.ceil((double)(exponent + Ieee.Double.SIGNIFICAND_SIZE - 1) * k1Log10 - 1e-10);
		return (int)estimate;
	}


	/**
	 *  Let v = significand * 2^exponent.
	 *  Computes v / 10^estimatedPower exactly, as a ratio of two bignums, numerator
	 *  and denominator. The functions generateShortestDigits and
	 *  generateCountedDigits will then convert this ratio to its decimal
	 *  representation d, with the required accuracy.
	 *  Then d * 10^estimatedPower is the representation of v.
	 *  (Note: the fraction and the estimatedPower might get adjusted before
	 *  generating the decimal representation.)
	 *
	 *  The initial start values consist of:
	 *   - a scaled numerator: s.t. numerator/denominator == v / 10^estimatedPower.
	 *   - a scaled (common) denominator.
	 *   optionally (used by generateShortestDigits to decide if it has the shortest
	 *   decimal converting back to v):
	 *   - v - m-: the distance to the lower boundary.
	 *   - m+ - v: the distance to the upper boundary.
	 *
	 *  v, m+, m-, and therefore v - m- and m+ - v all share the same denominator.
	 *
	 *  Let ep == estimatedPower, then the returned values will satisfy:
	 *   v / 10^ep = numerator / denominator.
	 *   v's boundaries m- and m+:
	 * 	m- / 10^ep == v / 10^ep - deltaMinus / denominator
	 * 	m+ / 10^ep == v / 10^ep + deltaPlus / denominator
	 *   Or in other words:
	 * 	m- == v - deltaMinus * 10^ep / denominator;
	 * 	m+ == v + deltaPlus * 10^ep / denominator;
	 *
	 *  Since 10^(k-1) <= v < 10^k    (with k == estimatedPower)
	 *   or       10^k <= v < 10^(k+1)
	 *   we then have 0.1 <= numerator/denominator < 1
	 * 		   or    1 <= numerator/denominator < 10
	 *
	 *  It is then easy to kickstart the digit-generation routine.
	 *
	 *  The boundary-deltas are only filled if the mode equals BIGNUM_DTOA_SHORTEST.
	 */
	private static void initialScaledStartValuesPositiveExponent(
			@Unsigned long significand, int exponent,
			int estimatedPower, boolean needBoundaryDeltas,
			Bignum numerator, Bignum denominator,
			Bignum deltaMinus, Bignum deltaPlus) {
		// A positive exponent implies a positive power.
		DOUBLE_CONVERSION_ASSERT(estimatedPower >= 0);
		// Since the estimatedPower is positive we simply multiply the denominator
		// by 10^estimatedPower.

		// numerator = v.
		numerator.assignUInt64(significand);
		numerator.shiftLeft(exponent);
		// denominator = 10^estimatedPower.
		denominator.assignPower(10, estimatedPower);

		if (needBoundaryDeltas) {
			// Introduce a common denominator so that the deltas to the boundaries are
			// integers.
			denominator.shiftLeft(1);
			numerator.shiftLeft(1);
			// Let v = f * 2^e, then m+ - v = 1/2 * 2^e; With the common
			// denominator (of 2) deltaPlus equals 2^e.
			deltaPlus.assignUInt(1);
			deltaPlus.shiftLeft(exponent);
			// Same for deltaMinus. The adjustments if f == 2^p-1 are done later.
			deltaMinus.assignUInt(1);
			deltaMinus.shiftLeft(exponent);
		}
	}


	/**
	 *  Let v = significand * 2^exponent.
	 *  Computes v / 10^estimatedPower exactly, as a ratio of two bignums, numerator
	 *  and denominator. The functions generateShortestDigits and
	 *  generateCountedDigits will then convert this ratio to its decimal
	 *  representation d, with the required accuracy.
	 *  Then d * 10^estimatedPower is the representation of v.
	 *  (Note: the fraction and the estimatedPower might get adjusted before
	 *  generating the decimal representation.)
	 *
	 *  The initial start values consist of:
	 *   - a scaled numerator: s.t. numerator/denominator == v / 10^estimatedPower.
	 *   - a scaled (common) denominator.
	 *   optionally (used by generateShortestDigits to decide if it has the shortest
	 *   decimal converting back to v):
	 *   - v - m-: the distance to the lower boundary.
	 *   - m+ - v: the distance to the upper boundary.
	 *
	 *  v, m+, m-, and therefore v - m- and m+ - v all share the same denominator.
	 *
	 *  Let ep == estimatedPower, then the returned values will satisfy:
	 *   v / 10^ep = numerator / denominator.
	 *   v's boundaries m- and m+:
	 * 	m- / 10^ep == v / 10^ep - deltaMinus / denominator
	 * 	m+ / 10^ep == v / 10^ep + deltaPlus / denominator
	 *   Or in other words:
	 * 	m- == v - deltaMinus * 10^ep / denominator;
	 * 	m+ == v + deltaPlus * 10^ep / denominator;
	 *
	 *  Since 10^(k-1) <= v < 10^k    (with k == estimatedPower)
	 *   or       10^k <= v < 10^(k+1)
	 *   we then have 0.1 <= numerator/denominator < 1
	 * 		   or    1 <= numerator/denominator < 10
	 *
	 *  It is then easy to kickstart the digit-generation routine.
	 *
	 *  The boundary-deltas are only filled if the mode equals BIGNUM_DTOA_SHORTEST.
	 */
	private static void initialScaledStartValuesNegativeExponentPositivePower(
			@Unsigned long ulSignificand, int exponent,
			int estimatedPower, boolean needBoundaryDeltas,
			Bignum numerator, Bignum denominator,
			Bignum deltaMinus, Bignum deltaPlus) {
		// v = f * 2^e with e < 0, and with estimatedPower >= 0.
		// This means that e is close to 0 (have a look at how estimatedPower is
		// computed).

		// numerator = significand
		//  since v = significand * 2^exponent this is equivalent to
		//  numerator = v * / 2^-exponent
		numerator.assignUInt64(ulSignificand);
		// denominator = 10^estimatedPower * 2^-exponent (with exponent < 0)
		denominator.assignPower(10, estimatedPower);
		denominator.shiftLeft(-exponent);

		if (needBoundaryDeltas) {
			// Introduce a common denominator so that the deltas to the boundaries are
			// integers.
			denominator.shiftLeft(1);
			numerator.shiftLeft(1);
			// Let v = f * 2^e, then m+ - v = 1/2 * 2^e; With the common
			// denominator (of 2) deltaPlus equals 2^e.
			// Given that the denominator already includes v's exponent the distance
			// to the boundaries is simply 1.
			deltaPlus.assignUInt(1);
			// Same for deltaMinus. The adjustments if f == 2^p-1 are done later.
			deltaMinus.assignUInt(1);
		}
	}


	/**
	 *  Let v = significand * 2^exponent.
	 *  Computes v / 10^estimatedPower exactly, as a ratio of two bignums, numerator
	 *  and denominator. The functions generateShortestDigits and
	 *  generateCountedDigits will then convert this ratio to its decimal
	 *  representation d, with the required accuracy.
	 *  Then d * 10^estimatedPower is the representation of v.
	 *  (Note: the fraction and the estimatedPower might get adjusted before
	 *  generating the decimal representation.)
	 *
	 *  The initial start values consist of:
	 *   - a scaled numerator: s.t. numerator/denominator == v / 10^estimatedPower.
	 *   - a scaled (common) denominator.
	 *   optionally (used by generateShortestDigits to decide if it has the shortest
	 *   decimal converting back to v):
	 *   - v - m-: the distance to the lower boundary.
	 *   - m+ - v: the distance to the upper boundary.
	 *
	 *  v, m+, m-, and therefore v - m- and m+ - v all share the same denominator.
	 *
	 *  Let ep == estimatedPower, then the returned values will satisfy:
	 *   v / 10^ep = numerator / denominator.
	 *   v's boundaries m- and m+:
	 * 	m- / 10^ep == v / 10^ep - deltaMinus / denominator
	 * 	m+ / 10^ep == v / 10^ep + deltaPlus / denominator
	 *   Or in other words:
	 * 	m- == v - deltaMinus * 10^ep / denominator;
	 * 	m+ == v + deltaPlus * 10^ep / denominator;
	 *
	 *  Since 10^(k-1) <= v < 10^k    (with k == estimatedPower)
	 *   or       10^k <= v < 10^(k+1)
	 *   we then have 0.1 <= numerator/denominator < 1
	 * 		   or    1 <= numerator/denominator < 10
	 *
	 *  It is then easy to kickstart the digit-generation routine.
	 *
	 *  The boundary-deltas are only filled if the mode equals BIGNUM_DTOA_SHORTEST.
	 */
	private static void initialScaledStartValuesNegativeExponentNegativePower(
			@Unsigned long ulSignificand, int exponent,
			int estimatedPower, boolean needBoundaryDeltas,
			Bignum numerator, Bignum denominator,
			Bignum deltaMinus, Bignum deltaPlus) {
		// Instead of multiplying the denominator with 10^estimatedPower we
		// multiply all values (numerator and deltas) by 10^-estimatedPower.

		// Use numerator as temporary container for powerTen.
		Bignum powerTen = numerator;
		powerTen.assignPower(10, -estimatedPower);

		if (needBoundaryDeltas) {
			// Since powerTen == numerator we must make a copy of 10^estimatedPower
			// before we complete the computation of the numerator.
			// deltaPlus = deltaMinus = 10^estimatedPower
			deltaPlus.assignBignum(powerTen);
			deltaMinus.assignBignum(powerTen);
		}

		// numerator = significand * 2 * 10^-estimatedPower
		//  since v = significand * 2^exponent this is equivalent to
		// numerator = v * 10^-estimatedPower * 2 * 2^-exponent.
		// Remember: numerator has been abused as powerTen. So no need to assign it
		//  to itself.
		DOUBLE_CONVERSION_ASSERT(numerator.equals(powerTen));
		numerator.multiplyByUInt64(ulSignificand);

		// denominator = 2 * 2^-exponent with exponent < 0.
		denominator.assignUInt(1);
		denominator.shiftLeft(-exponent);

		if (needBoundaryDeltas) {
			// Introduce a common denominator so that the deltas to the boundaries are
			// integers.
			numerator.shiftLeft(1);
			denominator.shiftLeft(1);
			// With this shift the boundaries have their correct value, since
			// deltaPlus = 10^-estimatedPower, and
			// deltaMinus = 10^-estimatedPower.
			// These assignments have been done earlier.
			// The adjustments if f == 2^p-1 (lower boundary is closer) are done later.
		}
	}

	/**
	 *  Let v = significand * 2^exponent.
	 *  Computes v / 10^estimatedPower exactly, as a ratio of two bignums, numerator
	 *  and denominator. The functions generateShortestDigits and
	 *  generateCountedDigits will then convert this ratio to its decimal
	 *  representation d, with the required accuracy.
	 *  Then d * 10^estimatedPower is the representation of v.
	 *  (Note: the fraction and the estimatedPower might get adjusted before
	 *  generating the decimal representation.)
	 *
	 *  The initial start values consist of:
	 *   - a scaled numerator: s.t. numerator/denominator == v / 10^estimatedPower.
	 *   - a scaled (common) denominator.
	 *   optionally (used by generateShortestDigits to decide if it has the shortest
	 *   decimal converting back to v):
	 *   - v - m-: the distance to the lower boundary.
	 *   - m+ - v: the distance to the upper boundary.
	 *
	 *  v, m+, m-, and therefore v - m- and m+ - v all share the same denominator.
	 *
	 *  Let ep == estimatedPower, then the returned values will satisfy:
	 *   v / 10^ep = numerator / denominator.
	 *   v's boundaries m- and m+:
	 * 	m- / 10^ep == v / 10^ep - deltaMinus / denominator
	 * 	m+ / 10^ep == v / 10^ep + deltaPlus / denominator
	 *   Or in other words:
	 * 	m- == v - deltaMinus * 10^ep / denominator;
	 * 	m+ == v + deltaPlus * 10^ep / denominator;
	 *
	 *  Since 10^(k-1) <= v < 10^k    (with k == estimatedPower)
	 *   or       10^k <= v < 10^(k+1)
	 *   we then have 0.1 <= numerator/denominator < 1
	 * 		   or    1 <= numerator/denominator < 10
	 *
	 *  It is then easy to kickstart the digit-generation routine.
	 *
	 *  The boundary-deltas are only filled if the mode equals BIGNUM_DTOA_SHORTEST.
	 */
	private static void initialScaledStartValues(@Unsigned long ulSignificand,
										 int exponent,
										 boolean lowerBoundaryIsCloser,
										 int estimatedPower,
										 boolean needBoundaryDeltas,
										 Bignum numerator,
										 Bignum denominator,
										 Bignum deltaMinus,
										 Bignum deltaPlus) {
		if (exponent >= 0) {
			initialScaledStartValuesPositiveExponent(
					ulSignificand, exponent, estimatedPower, needBoundaryDeltas,
					numerator, denominator, deltaMinus, deltaPlus);
		} else if (estimatedPower >= 0) {
			initialScaledStartValuesNegativeExponentPositivePower(
					ulSignificand, exponent, estimatedPower, needBoundaryDeltas,
					numerator, denominator, deltaMinus, deltaPlus);
		} else {
			initialScaledStartValuesNegativeExponentNegativePower(
					ulSignificand, exponent, estimatedPower, needBoundaryDeltas,
					numerator, denominator, deltaMinus, deltaPlus);
		}

		if (needBoundaryDeltas && lowerBoundaryIsCloser) {
			// The lower boundary is closer at half the distance of "normal" numbers.
			// Increase the common denominator and adapt all but the deltaMinus.
			denominator.shiftLeft(1);  // *2
			numerator.shiftLeft(1);    // *2
			deltaPlus.shiftLeft(1);   // *2
		}
	}



	/**
	 *  Multiplies numerator/denominator so that its values lies in the range 1-10.
	 *  Returns decimalPoint s.t.
	 *   v = numerator'/denominator' * 10^(decimalPoint-1)
	 * 	 where numerator' and denominator' are the values of numerator and
	 * 	 denominator after the call to this function.
	 *
	 *  This routine multiplies numerator/denominator so that its values lies in the
	 *  range 1-10. That is after a call to this function we have:
	 * 	1 <= (numerator + deltaPlus) /denominator < 10.
	 *  Let numerator the input before modification and numerator' the argument
	 *  after modification, then the output-parameter decimalPoint is such that
	 *   numerator / denominator * 10^estimatedPower ==
	 * 	numerator' / denominator' * 10^(decimalPoint - 1)
	 *  In some cases estimatedPower was too low, and this is already the case. We
	 *  then simply adjust the power so that 10^(k-1) <= v < 10^k (with k ==
	 *  estimatedPower) but do not touch the numerator or denominator.
	 *  Otherwise the routine multiplies the numerator and the deltas by 10.
	 */
	private static void fixupMultiply10(int estimatedPower, boolean isEven,
								int[] decimalPoint,
								Bignum numerator, Bignum denominator,
								Bignum deltaMinus, Bignum deltaPlus) {
		boolean inRange;
		if (isEven) {
			// For IEEE doubles half-way cases (in decimal system numbers ending with 5)
			// are rounded to the closest floating-point number with even significand.
			inRange = Bignum.plusCompare(numerator, deltaPlus, denominator) >= 0;
		} else {
			inRange = Bignum.plusCompare(numerator, deltaPlus, denominator) > 0;
		}
		if (inRange) {
			// Since numerator + deltaPlus >= denominator we already have
			// 1 <= numerator/denominator < 10. Simply update the estimatedPower.
    		decimalPoint[0] = estimatedPower + 1;
		} else {
    		decimalPoint[0] = estimatedPower;
			numerator.times10();
			if (Bignum.equal(deltaMinus, deltaPlus)) {
				deltaMinus.times10();
				deltaPlus.assignBignum(deltaMinus);
			} else {
				deltaMinus.times10();
				deltaPlus.times10();
			}
		}
	}



}
