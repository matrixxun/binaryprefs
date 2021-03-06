package com.ironz.binaryprefs.serialization.serializer;

/**
 * {@code String} to byte array implementation and backwards
 */
public final class StringSerializer {

    /**
     * Uses for detecting byte array type of {@link String}
     */
    private static final byte FLAG = -2;
    /**
     * Minimum size primitive type of {@link String}
     */
    private static final int SIZE = 1;

    /**
     * Serialize {@code String} into byte array with following scheme:
     * [{@link #FLAG}] + [string_byte_array].
     *
     * @param s target String to serialize.
     * @return specific byte array with scheme.
     */
    public byte[] serialize(String s) {
        byte[] stringBytes = s.getBytes();
        int flagSize = 1;
        byte[] b = new byte[stringBytes.length + flagSize];
        b[0] = FLAG;
        System.arraycopy(stringBytes, 0, b, flagSize, stringBytes.length);
        return b;
    }

    /**
     * Deserialize byte by {@link #serialize(String)} convention
     *
     * @param bytes target byte array for deserialization
     * @return deserialized String
     */
    public String deserialize(byte[] bytes) {
        return deserialize(bytes, 0, bytes.length - 1);
    }

    /**
     * Deserialize byte by {@link #serialize(String)} convention
     *
     * @param bytes  target byte array for deserialization
     * @param offset bytes array offset
     * @param length bytes array length
     * @return deserialized String
     */
    public String deserialize(byte[] bytes, int offset, int length) {
        int flagOffset = 1;
        return new String(bytes, flagOffset + offset, length);
    }

    public boolean isMatches(byte flag) {
        return flag == FLAG;
    }

    public int bytesLength() {
        return SIZE;
    }
}