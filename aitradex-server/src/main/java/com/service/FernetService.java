package com.service;

import com.config.AppProperties;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class FernetService {
    private final byte[] signingKey;
    private final byte[] encryptionKey;
    private final SecureRandom random = new SecureRandom();

    public FernetService(AppProperties properties) {
        String rawKey = properties.getCredentialsFernetKey();
        if (rawKey == null || rawKey.isBlank()) {
            this.signingKey = null;
            this.encryptionKey = null;
            return;
        }
        byte[] key = Base64.getUrlDecoder().decode(rawKey);
        if (key.length != 32) {
            throw new IllegalStateException("invalid_fernet_key");
        }
        this.signingKey = Arrays.copyOfRange(key, 0, 16);
        this.encryptionKey = Arrays.copyOfRange(key, 16, 32);
    }

    public String encrypt(String value) {
        if (value == null || value.isBlank()) return null;
        ensureConfigured();
        try {
            byte version = (byte) 0x80;
            long seconds = Instant.now().getEpochSecond();
            byte[] timestamp = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(seconds).array();
            byte[] iv = new byte[16];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new IvParameterSpec(iv));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));

            byte[] message = ByteBuffer.allocate(1 + 8 + 16 + ciphertext.length)
                    .put(version)
                    .put(timestamp)
                    .put(iv)
                    .put(ciphertext)
                    .array();
            byte[] signature = sign(message);
            byte[] token = ByteBuffer.allocate(message.length + signature.length).put(message).put(signature).array();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("fernet_encrypt_failed", e);
        }
    }

    public String decrypt(String token) {
        if (token == null || token.isBlank()) return null;
        ensureConfigured();
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(token);
            if (decoded.length < 1 + 8 + 16 + 32) {
                throw new IllegalStateException("invalid_fernet_token");
            }
            byte version = decoded[0];
            if (version != (byte) 0x80) {
                throw new IllegalStateException("invalid_fernet_version");
            }
            byte[] payload = Arrays.copyOfRange(decoded, 0, decoded.length - 32);
            byte[] signature = Arrays.copyOfRange(decoded, decoded.length - 32, decoded.length);
            byte[] expected = sign(payload);
            if (!MessageDigest.isEqual(signature, expected)) {
                throw new IllegalStateException("invalid_fernet_signature");
            }
            byte[] iv = Arrays.copyOfRange(decoded, 9, 25);
            byte[] ciphertext = Arrays.copyOfRange(decoded, 25, decoded.length - 32);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new IvParameterSpec(iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("fernet_decrypt_failed", e);
        }
    }

    private byte[] sign(byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private void ensureConfigured() {
        if (signingKey == null || encryptionKey == null) {
            throw new IllegalStateException("credentials_fernet_key_not_configured");
        }
    }
}
