package org.apache.lucene.search.suggest.fst;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.lucene.search.suggest.fst.Sort.BufferSize;
import org.apache.lucene.search.suggest.fst.Sort.ByteSequencesWriter;
import org.apache.lucene.search.suggest.fst.Sort.SortInfo;
import org.apache.lucene.util.*;
import org.junit.*;

/**
 * Tests for on-disk merge sorting.
 */
public class TestSort extends LuceneTestCase {
  private File tempDir;

  @Before
  public void prepareTempDir() throws IOException {
    tempDir = _TestUtil.getTempDir("mergesort");
    _TestUtil.rmDir(tempDir);
    tempDir.mkdirs();
  }
  
  @After
  public void cleanup() throws IOException {
    if (tempDir != null)
      _TestUtil.rmDir(tempDir);
  }

  @Test
  public void testEmpty() throws Exception {
    checkSort(new Sort(), new byte [][] {});
  }

  @Test
  public void testSingleLine() throws Exception {
    checkSort(new Sort(), new byte [][] {
        "Single line only.".getBytes("UTF-8")
    });
  }

  @Test
  public void testIntermediateMerges() throws Exception {
    // Sort 20 mb worth of data with 1mb buffer, binary merging.
    SortInfo info = checkSort(new Sort(BufferSize.megabytes(1), Sort.defaultTempDir(), 2), 
        generateRandom(Sort.MB * 20));
    assertTrue(info.mergeRounds > 10);
  }

  @Test
  public void testSmallRandom() throws Exception {
    // Sort 20 mb worth of data with 1mb buffer.
    SortInfo sortInfo = checkSort(new Sort(BufferSize.megabytes(1), Sort.defaultTempDir(), Sort.MAX_TEMPFILES), 
        generateRandom(Sort.MB * 20));
    assertEquals(1, sortInfo.mergeRounds);
  }

  @Test @Nightly
  public void testLargerRandom() throws Exception {
    // Sort 100MB worth of data with 15mb buffer.
    checkSort(new Sort(BufferSize.megabytes(16), Sort.defaultTempDir(), Sort.MAX_TEMPFILES), 
        generateRandom(Sort.MB * 100));
  }

  private byte[][] generateRandom(int howMuchData) {
    ArrayList<byte[]> data = new ArrayList<byte[]>(); 
    while (howMuchData > 0) {
      byte [] current = new byte [random.nextInt(256)];
      random.nextBytes(current);
      data.add(current);
      howMuchData -= current.length;
    }
    byte [][] bytes = data.toArray(new byte[data.size()][]);
    return bytes;
  }

  /**
   * Check sorting data on an instance of {@link Sort}.
   */
  private SortInfo checkSort(Sort sort, byte[][] data) throws IOException {
    File unsorted = writeAll("unsorted", data);

    Arrays.sort(data, Sort.unsignedByteOrderComparator);
    File golden = writeAll("golden", data);

    File sorted = new File(tempDir, "sorted");
    SortInfo sortInfo = sort.sort(unsorted, sorted);
    //System.out.println("Input size [MB]: " + unsorted.length() / (1024 * 1024));
    //System.out.println(sortInfo);

    assertFilesIdentical(golden, sorted);
    return sortInfo;
  }

  /**
   * Make sure two files are byte-byte identical.
   */
  private void assertFilesIdentical(File golden, File sorted) throws IOException {
    assertEquals(golden.length(), sorted.length());

    byte [] buf1 = new byte [64 * 1024];
    byte [] buf2 = new byte [64 * 1024];
    int len;
    DataInputStream is1 = new DataInputStream(new FileInputStream(golden));
    DataInputStream is2 = new DataInputStream(new FileInputStream(sorted));
    while ((len = is1.read(buf1)) > 0) {
      is2.readFully(buf2, 0, len);
      for (int i = 0; i < len; i++) {
        assertEquals(buf1[i], buf2[i]);
      }
    }
    IOUtils.close(is1, is2);
  }

  private File writeAll(String name, byte[][] data) throws IOException {
    File file = new File(tempDir, name);
    ByteSequencesWriter w = new Sort.ByteSequencesWriter(file);
    for (byte [] datum : data) {
      w.write(datum);
    }
    w.close();
    return file;
  }
}
