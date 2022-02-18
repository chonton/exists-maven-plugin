package org.honton.chas.exists;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Set;

/** Calculate digest for a file. */
public class CheckSum {
  private static final int BUFFER_SIZE = 0x10000;
  private static final char[] HEX_DIGITS = {
    '0', '1', '2', '3', '4', '5',
    '6', '7', '8', '9', 'a', 'b',
    'c', 'd', 'e', 'f'
  };
  private final MessageDigest digest;

  public CheckSum() throws NoSuchAlgorithmException {
    digest = MessageDigest.getInstance("SHA-1");
  }

  private static String hexEncode(byte[] bytes) {
    int cOffset = bytes.length * 2;
    char[] chars = new char[cOffset];
    for (int bOffset = bytes.length; --bOffset >= 0; ) {
      int c = bytes[bOffset] & 0xff;
      chars[--cOffset] = HEX_DIGITS[c & 0xf];
      chars[--cOffset] = HEX_DIGITS[c >> 4];
    }
    return new String(chars);
  }

  public byte[] getChecksumBytes(Path path) throws IOException {
    try (ByteChannel byteChannel = Files.newByteChannel(path, StandardOpenOption.READ)) {
      digest.reset();
      readStream(byteChannel);
      return digest.digest();
    }
  }

  public String getChecksum(Path path) throws IOException {
    return hexEncode(getChecksumBytes(path));
  }

  private void readStream(ByteChannel byteChannel) throws IOException {
    ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    for (; ; ) {
      int bytes = byteChannel.read(byteBuffer);
      if (bytes < 0) {
        break;
      }
      byteBuffer.flip();
      digest.update(byteBuffer);
      byteBuffer.clear();
    }
  }

  public void writeChecksum(Path path) throws IOException {
    Set<String> lines = Collections.singleton(getChecksum(path));
    Path sibling = path.resolveSibling(path.getFileName() + ".sha1");
    Files.write(
        sibling,
        lines,
        StandardCharsets.US_ASCII,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING);
  }
}
