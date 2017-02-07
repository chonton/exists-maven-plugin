package org.honton.chas.exists;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.xml.bind.DatatypeConverter;
import org.codehaus.plexus.util.IOUtil;

/**
 * Calculate digest for a file.
 */
public class CheckSum {
  private static final int BUFFER_SIZE = 0x10000;

  private MessageDigest digest;

  public CheckSum(String digestAlgorithm) throws NoSuchAlgorithmException {
    digest = MessageDigest.getInstance(digestAlgorithm);
  }

  public String getChecksum(File file) throws IOException {
    FileInputStream fis = new FileInputStream(file);
    try {
      digest.reset();
      readStream(fis);
      return DatatypeConverter.printHexBinary(digest.digest());
    } finally {
      IOUtil.close(fis);
    }
  }

  private void readStream(FileInputStream fis) throws IOException {
    FileChannel fileChannel = fis.getChannel();
    ByteBuffer byteBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    for (; ; ) {
      int bytes = fileChannel.read(byteBuffer);
      if (bytes < 0) {
        break;
      }
      byteBuffer.flip();
      digest.update(byteBuffer);
      byteBuffer.clear();
    }
  }
}
