package com.lybia.cryptowallet.coinkits.hdwallet.core.helpers;


import java.util.Arrays;

public class Base58 {

    public enum Base58Type {
        Basic,
        Ripple;

        public char[] ALPHABET() {
            char[] rs = new char[128];
            switch (this) {
                case Basic:
                    return "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
                case Ripple:
                    return "rpshnaf39wBUDNEGHJKLM4PQRST7VWXYZ2bcdeCg65jkm8oFqi1tuvAxyz".toCharArray();
            }
            return rs;
        }

        public char ENCODED_ZERO() {
            return ALPHABET()[0];
        }

        public int[] INDEXES() {
            int[] rs = new int[128];
            Arrays.fill(rs, -1);
            for (int i = 0; i < ALPHABET().length; i++) {
                rs[ALPHABET()[i]] = i;
            }
            return rs;
        }
    }

    public static String encode(byte[] input) {
        return encode(input, Base58Type.Basic);
    }

    public static String encode(byte[] input, Base58Type type) {
        if (input.length == 0) {
            return "";
        }
        // Count leading zeros.
        int zeros = 0;
        while (zeros < input.length && input[zeros] == 0) {
            ++zeros;
        }
        // Convert base-256 digits to base-58 digits (plus conversion to ASCII characters)
        input = Arrays.copyOf(input, input.length); // since we modify it in-place
        char[] encoded = new char[input.length * 2]; // upper bound
        int outputStart = encoded.length;
        for (int inputStart = zeros; inputStart < input.length; ) {
            encoded[--outputStart] = type.ALPHABET()[divmod(input, inputStart, 256, 58)];
            if (input[inputStart] == 0) {
                ++inputStart; // optimization - skip leading zeros
            }
        }
        // Preserve exactly as many leading encoded zeros in output as there were leading zeros in input.
        while (outputStart < encoded.length && encoded[outputStart] == type.ENCODED_ZERO()) {
            ++outputStart;
        }
        while (--zeros >= 0) {
            encoded[--outputStart] = type.ENCODED_ZERO();
        }
        // Return encoded string (including encoded leading zeros).
        return new String(encoded, outputStart, encoded.length - outputStart);
    }

    public static byte[] decode(String input){
        return  decode(input, Base58Type.Basic);
    }

    public static byte[] decode(String input, Base58Type type) {
        if (input.length() == 0) {
            return new byte[0];
        }
        // Convert the base58-encoded ASCII chars to a base58 byte sequence (base58 digits).
        byte[] input58 = new byte[input.length()];
        for (int i = 0; i < input.length(); ++i) {
            char c = input.charAt(i);
            int digit = c < 128 ? type.INDEXES()[c] : -1;
            if (digit < 0) {
                return new byte[0];
            }
            input58[i] = (byte) digit;
        }
        // Count leading zeros.
        int zeros = 0;
        while (zeros < input58.length && input58[zeros] == 0) {
            ++zeros;
        }
        // Convert base-58 digits to base-256 digits.
        byte[] decoded = new byte[input.length()];
        int outputStart = decoded.length;
        for (int inputStart = zeros; inputStart < input58.length; ) {
            decoded[--outputStart] = divmod(input58, inputStart, 58, 256);
            if (input58[inputStart] == 0) {
                ++inputStart; // optimization - skip leading zeros
            }
        }
        // Ignore extra leading zeroes that were added during the calculation.
        while (outputStart < decoded.length && decoded[outputStart] == 0) {
            ++outputStart;
        }
        // Return decoded data (including original number of leading zeros).
        return Arrays.copyOfRange(decoded, outputStart - zeros, decoded.length);
    }

    private static byte divmod(byte[] number, int firstDigit, int base, int divisor) {
        // this is just long division which accounts for the base of the input digits
        int remainder = 0;
        for (int i = firstDigit; i < number.length; i++) {
            int digit = (int) number[i] & 0xFF;
            int temp = remainder * base + digit;
            number[i] = (byte) (temp / divisor);
            remainder = temp % divisor;
        }
        return (byte) remainder;
    }
}
