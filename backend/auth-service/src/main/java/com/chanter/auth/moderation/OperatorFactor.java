package com.chanter.auth.moderation;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** RFC 6238, 30-second HMAC-SHA1 codes; a separate operator key encrypts enrolled secrets. */
@Component
public class OperatorFactor {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public OperatorFactor(@Value("${chanter.moderation.operator-key:${CHANTER_OPERATOR_ENCRYPTION_KEY:}}") String configuredKey) {
        key = configuredKey.isBlank() ? null : Base64.getDecoder().decode(configuredKey);
        if (key != null && key.length != 32) throw new IllegalArgumentException("Operator encryption key must be 32 base64-encoded bytes");
    }

    public byte[] newSecret() {
        byte[] secret = new byte[20];
        random.nextBytes(secret);
        return secret;
    }

    public String encrypt(UUID user, byte[] secret) {
        requireConfigured();
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(user.toString().getBytes(StandardCharsets.US_ASCII));
            byte[] encrypted = cipher.doFinal(secret);
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("Could not protect operator factor", failure);
        }
    }

    public byte[] decrypt(UUID user, String encrypted) {
        requireConfigured();
        try {
            byte[] packed = Base64.getDecoder().decode(encrypted);
            if (packed.length < 29) throw new GeneralSecurityException("Invalid factor envelope");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, packed, 0, 12));
            cipher.updateAAD(user.toString().getBytes(StandardCharsets.US_ASCII));
            return cipher.doFinal(packed, 12, packed.length - 12);
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            throw new IllegalStateException("Could not read protected operator factor", failure);
        }
    }

    public static String code(byte[] secret, long counter, int digits) {
        if (digits != 6 && digits != 8) throw new IllegalArgumentException("Unsupported code length");
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hmac = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
            int offset = hmac[hmac.length - 1] & 15;
            int truncated = ByteBuffer.wrap(hmac, offset, 4).getInt() & 0x7fffffff;
            return String.format(Locale.ROOT, "%0" + digits + "d", truncated % (digits == 6 ? 1_000_000 : 100_000_000));
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static String base32(byte[] secret) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte value : secret) {
            buffer = (buffer << 8) | (value & 255);
            bits += 8;
            while (bits >= 5) {
                bits -= 5;
                result.append(alphabet.charAt((buffer >>> bits) & 31));
            }
        }
        if (bits > 0) result.append(alphabet.charAt((buffer << (5 - bits)) & 31));
        return result.toString();
    }

    private void requireConfigured() {
        if (key == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Operator verification is not configured");
    }
}
