package com.github.cuixudong74.reproduciblebenchharnessjava.bench.core;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 确定性输入生成器：payload 仅由 (seed, index, inputSize) 决定，
 * 与调用顺序、线程、运行时刻无关。
 *
 * 算法：对 "seed || index || blockCounter" 做 SHA-256，逐块拼接至所需长度。
 */
public final class InputGenerator {

    private InputGenerator() {
    }

    public static byte[] payload(long seed, long index, int inputSize) {
        MessageDigest digest = sha256();
        byte[] out = new byte[inputSize];
        int offset = 0;
        long block = 0;
        while (offset < inputSize) {
            byte[] material = digest.digest(
                    ByteBuffer.allocate(Long.BYTES * 3).putLong(seed).putLong(index).putLong(block).array());
            int n = Math.min(material.length, inputSize - offset);
            System.arraycopy(material, 0, out, offset, n);
            offset += n;
            block++;
        }
        return out;
    }

    /** 供工作负载复用的确定性摘要工具。 */
    public static String sha256Hex(byte[] data) {
        byte[] hash = sha256().digest(data);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
