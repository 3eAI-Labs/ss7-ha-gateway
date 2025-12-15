package com.company.ss7ha.core.utils;

/**
 * Utility for GSM 7-bit alphabet encoding and decoding (packing/unpacking).
 * Reference: 3GPP TS 23.038
 */
public class Gsm7BitCharset {

    // Standard GSM 7-bit alphabet mapping
    private static final char[] GSM_7BIT_CHARS = {
        '@', '£', '$', '¥', 'è', 'é', 'ù', 'ì', 'ò', 'Ç', '\n', 'Ø', 'ø', '\r', 'Å', 'å',
        'Δ', '_', 'Φ', 'Γ', 'Λ', 'Ω', 'Π', 'Ψ', 'Σ', 'Θ', 'Ξ', '\u001B', 'Æ', 'æ', 'ß', 'É',
        ' ', '!', '"', '#', '¤', '%', '&', '\'', '(', ')', '*', '+', ',', '-', '.', '/',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ':', ';', '<', '=', '>', '?',
        '¡', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O',
        'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 'Ä', 'Ö', 'Ñ', 'Ü', '§',
        '¿', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o',
        'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 'ä', 'ö', 'ñ', 'ü', 'à'
    };

    /**
     * Packs a string of characters into GSM 7-bit packed format (septets).
     * 8 characters (8 * 7 = 56 bits) fit into 7 bytes (7 * 8 = 56 bits).
     */
    public static byte[] pack(String text) {
        if (text == null || text.isEmpty()) {
            return new byte[0];
        }

        // Convert string to 7-bit values
        byte[] septets = stringToSeptets(text);
        
        // Pack septets into octets
        int septetCount = septets.length;
        int octetCount = (septetCount * 7 + 7) / 8;
        byte[] octets = new byte[octetCount];

        for (int i = 0; i < septetCount; i++) {
            int shift = i % 8;
            int octetIndex = (i * 7) / 8;
            
            // Current septet value
            int value = septets[i] & 0x7F;

            if (shift == 0) {
                octets[octetIndex] = (byte) value;
            } else {
                // Combine with previous octet
                octets[octetIndex] |= (byte) (value << shift);
                
                // If part of the septet spills into the next octet
                if (octetIndex + 1 < octetCount) {
                    octets[octetIndex + 1] = (byte) (value >> (8 - shift));
                }
            }
        }
        return octets;
    }

    /**
     * Unpacks GSM 7-bit packed data into a String.
     */
    public static String unpack(byte[] octets, int lengthChars) {
        if (octets == null || octets.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder(lengthChars);
        
        for (int i = 0; i < lengthChars; i++) {
            int octetIndex = (i * 7) / 8;
            int shift = i % 8;
            
            int value = octets[octetIndex] & 0xFF;
            
            // If checking next octet
            if (shift > 1 && octetIndex + 1 < octets.length) {
                value |= (octets[octetIndex + 1] & 0xFF) << 8;
            }
            
            // Extract 7 bits
            int septet = (value >> shift) & 0x7F;
            
            sb.append(septetToChar(septet));
        }
        
        return sb.toString();
    }

    private static byte[] stringToSeptets(String text) {
        byte[] septets = new byte[text.length()];
        for (int i = 0; i < text.length(); i++) {
            septets[i] = charToSeptet(text.charAt(i));
        }
        return septets;
    }

    private static byte charToSeptet(char c) {
        for (int i = 0; i < GSM_7BIT_CHARS.length; i++) {
            if (GSM_7BIT_CHARS[i] == c) {
                return (byte) i;
            }
        }
        // Default to space if not found or handling of extended chars not fully implemented here
        return 0x20;
    }

    private static char septetToChar(int septet) {
        if (septet >= 0 && septet < GSM_7BIT_CHARS.length) {
            return GSM_7BIT_CHARS[septet];
        }
        return '?';
    }
}