package dev.achiri.multivault.document.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentHashUtilTest {

    @Test
    void sha256HexProducesCorrectHash() {
        byte[] input = "hello world".getBytes();
        String hash = DocumentHashUtil.sha256Hex(input);
        assertThat(hash).isEqualTo("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9");
    }

    @Test
    void sha256HexEmptyInput() {
        String hash = DocumentHashUtil.sha256Hex(new byte[0]);
        assertThat(hash).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    @Test
    void sha256HexProduces64CharHexString() {
        byte[] input = "test data".getBytes();
        String hash = DocumentHashUtil.sha256Hex(input);
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void sha256HexDifferentInputsProduceDifferentHashes() {
        String hash1 = DocumentHashUtil.sha256Hex("input a".getBytes());
        String hash2 = DocumentHashUtil.sha256Hex("input b".getBytes());
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void sha256HexSameInputProducesSameHash() {
        byte[] input = "consistent".getBytes();
        String hash1 = DocumentHashUtil.sha256Hex(input);
        String hash2 = DocumentHashUtil.sha256Hex(input);
        assertThat(hash1).isEqualTo(hash2);
    }
}
