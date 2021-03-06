package com.fasterxml.util.membuf.longs;

import org.junit.Assert;

import com.fasterxml.util.membuf.*;

/**
 * Tests for testing both chunky and streamy long-valued memory buffers for
 * basic append and read combinations.
 */
public class AppendReadLongsTest extends MembufTestBase
{
    public void testSimpleAppendAndGet() throws Exception
    {
        _testSimpleAppendAndGet(SegType.BYTE_BUFFER_DIRECT);
        _testSimpleAppendAndGet(SegType.BYTE_BUFFER_FAKE);
        _testSimpleAppendAndGet(SegType.BYTE_ARRAY);
    }

    public void testSimpleAppendAndRead() throws Exception
    {
        _testSimpleAppendAndRead(SegType.BYTE_BUFFER_DIRECT);
        _testSimpleAppendAndRead(SegType.BYTE_BUFFER_FAKE);
        _testSimpleAppendAndRead(SegType.BYTE_ARRAY);
    }    

    public void testEmptySegments() throws Exception
    {
        _testEmptySegments(SegType.BYTE_BUFFER_DIRECT);
        _testEmptySegments(SegType.BYTE_BUFFER_FAKE);
        _testEmptySegments(SegType.BYTE_ARRAY);
    }

    public void testTryReadFromEmpty() throws Exception
    {
        _testTryReadFromEmpty(SegType.BYTE_BUFFER_DIRECT);
        _testTryReadFromEmpty(SegType.BYTE_BUFFER_FAKE);
        _testTryReadFromEmpty(SegType.BYTE_ARRAY);
    }

    // And similarly for streamy buffer; note that not all are applicable
    // (no empty segments; another test is redundant)

    public void testStreamyAppendAndGet() throws Exception
    {
        _testStreamyAppendAndGet(SegType.BYTE_BUFFER_DIRECT);
        _testStreamyAppendAndGet(SegType.BYTE_BUFFER_FAKE);
        _testStreamyAppendAndGet(SegType.BYTE_ARRAY);
    }

    public void testStreamyAppendAndGet2() throws Exception
    {
        _testStreamyAppendAndGet2(SegType.BYTE_BUFFER_DIRECT);
        _testStreamyAppendAndGet2(SegType.BYTE_BUFFER_FAKE);
        _testStreamyAppendAndGet2(SegType.BYTE_ARRAY);
    }
    
    public void testStreamyReadFromEmpty() throws Exception
    {
        _testStreamyReadFromEmpty(SegType.BYTE_BUFFER_DIRECT);
        _testStreamyReadFromEmpty(SegType.BYTE_BUFFER_FAKE);
        _testStreamyReadFromEmpty(SegType.BYTE_ARRAY);
    }
    
    /*
    /**********************************************************************
    /* Actual test impls
    /**********************************************************************
     */

    private void _testSimpleAppendAndGet(SegType aType) throws Exception
    {
        // will use segments of size 10 bytes; only one segment per-allocator reuse
        // and maximum allocation of 4 segments per-allocator
        final MemBuffersForLongs bufs = createLongsBuffers(aType, 10, 1, 4);
        // buffer will have similar limits
        final ChunkyLongsMemBuffer buffer = bufs.createChunkyBuffer(1, 3);

        assertEquals(0, buffer.getEntryCount());
        assertEquals(0, buffer.getTotalPayloadLength());
        assertTrue(buffer.isEmpty());
        // no content, beginning of buffer, all 30 bytes available...
        assertEquals(30, buffer.getMaximumAvailableSpace());
        assertEquals(-1, buffer.getNextEntryLength());

        assertNull(buffer.getNextEntryIfAvailable());
        
        long[] chunk3 = buildLongsChunk(3);
        buffer.appendEntry(chunk3);
        // should take 4 bytes (length, payload)
        assertEquals(1, buffer.getEntryCount());
        assertEquals(1, buffer.getSegmentCount());
        assertEquals(3, buffer.getTotalPayloadLength());
        assertEquals(26, buffer.getMaximumAvailableSpace());

        // and then let's just read it off
        long[] actual = buffer.getNextEntry();
        assertNotNull(actual);
        verifyChunk(actual, chunk3.length);

        // but then append two 7 byte segments
        long[] chunk7 = buildLongsChunk(7);
        buffer.appendEntry(chunk7);
        buffer.appendEntry(chunk7);
        assertEquals(2, buffer.getEntryCount());
        assertEquals(14, buffer.getTotalPayloadLength());

        // and third one as well
        buffer.appendEntry(chunk7);
        assertEquals(3, buffer.getEntryCount());
        assertEquals(21, buffer.getTotalPayloadLength());

        // then read them all
        assertEquals(7, buffer.getNextEntryLength());
        // repeat to ensure length is not reset (or re-read)
        assertEquals(7, buffer.getNextEntryLength());
        assertEquals(7, buffer.getNextEntryLength());
        actual = buffer.getNextEntry();
        verifyChunk(actual, chunk7.length);
        actual = buffer.getNextEntry();
        verifyChunk(actual, chunk7.length);
        assertEquals(7, buffer.getNextEntryLength());
        actual = buffer.getNextEntry();
        verifyChunk(actual, chunk7.length);

        // and now we should be empty...
        assertEquals(0, buffer.getEntryCount());
        assertEquals(0, buffer.getTotalPayloadLength());
        assertTrue(buffer.isEmpty());
        // including holding on to just one segment
        assertEquals(1, buffer.getSegmentCount());

        // and shouldn't find anything else, for now
        assertNull(buffer.getNextEntryIfAvailable());
    }

    // Test 'read' methods (where called hands buffer to use)
    private void _testSimpleAppendAndRead(SegType aType) throws Exception
    {
        final MemBuffersForLongs bufs = createLongsBuffers(aType, 10, 1, 4);
        final ChunkyLongsMemBuffer buffer = bufs.createChunkyBuffer(1, 3);

        assertEquals(0, buffer.getEntryCount());
        assertEquals(0, buffer.getTotalPayloadLength());
        assertTrue(buffer.isEmpty());
        // first, reads from empty buffer should fail as usual
        assertEquals(Integer.MIN_VALUE, buffer.readNextEntryIfAvailable(new long[1], 0));

        // then append a 2-byte segment
        long[] data = { 1, 2 };
        buffer.appendEntry(data);
        assertEquals(1, buffer.getEntryCount());
        assertEquals(2, buffer.getTotalPayloadLength());
        assertFalse(buffer.isEmpty());

        // and try read; first with unsufficient buffer
        assertEquals(-2, buffer.readNextEntryIfAvailable(new long[1], 0));
        long[] result = new long[2];
        assertEquals(-2, buffer.readNextEntryIfAvailable(result, 1));

        // but succeed with enough space
        assertEquals(2, buffer.readNextEntryIfAvailable(result, 0));
        assertEquals(1L, result[0]);
        assertEquals(2L, result[1]);
        assertEquals(0, buffer.getEntryCount());
        assertEquals(0, buffer.getTotalPayloadLength());
        assertTrue(buffer.isEmpty());

        // then verify that split read works too
        data = new long[25];
        for (int i = 0; i < data.length; ++i) {
            data[i] = (byte) i;
        }
        buffer.appendEntry(data);
        
        result = new long[25];
        assertEquals(25, buffer.readNextEntry(10L, result, 0));
        Assert.assertArrayEquals(data, result);
    }
    
    /**
     * Separate test for appending and reading empty segments; segments
     * with 0 bytes of payload which consist of just a single length
     * indicator byte.
     */
    private void _testEmptySegments(SegType aType) throws Exception
    {
        final MemBuffersForLongs bufs = createLongsBuffers(aType, 10, 1, 3);
        final ChunkyLongsMemBuffer buffer = bufs.createChunkyBuffer(1, 2);
        long[] empty = new long[0];

        assertEquals(0, buffer.getEntryCount());
        assertEquals(1, buffer.getSegmentCount());
        assertEquals(20, buffer.getMaximumAvailableSpace());

        // should be able to append 20 of empties...
        for (int i = 0; i < 20; ++i) {
            buffer.appendEntry(empty);
        }
        assertEquals(20, buffer.getEntryCount());
        assertEquals(2, buffer.getSegmentCount());

        for (int i = 0; i < 20; ++i) {
            long[] data = buffer.getNextEntry();
            assertEquals(0, data.length);
        }
        assertEquals(0, buffer.getEntryCount());
        assertTrue(buffer.isEmpty());
        assertEquals(1, buffer.getSegmentCount());
    }

    /**
     * Unit test that verifies that read from empty buffer
     * would block; use timeout as verification
     */
    private void _testTryReadFromEmpty(SegType aType) throws Exception
    {
        final ChunkyLongsMemBuffer buffer = createLongsBuffers(aType, 1000, 1, 100).createChunkyBuffer(1, 2);
        
        long[] data = buffer.getNextEntryIfAvailable();
        assertNull(data);
        data = buffer.getNextEntry(10L); // 10 msecs delay
        assertNull(data);
    }

    /*
    /**********************************************************************
    /* Actual test impls, streamy
    /**********************************************************************
     */

    private void _testStreamyAppendAndGet(SegType aType) throws Exception
    {
        // will use segments of size 10 longs; only one segment per-allocator reuse
        // and maximum allocation of 4 segments per-allocator
        final MemBuffersForLongs bufs = createLongsBuffers(aType, 10, 1, 4);
        // buffer will have similar limits
        final StreamyLongsMemBuffer buffer = bufs.createStreamyBuffer(1, 3);

        assertEquals(0, buffer.getTotalPayloadLength());
        assertTrue(buffer.isEmpty());
        // no content, beginning of buffer, all 30 longs available...
        assertEquals(30, buffer.getMaximumAvailableSpace());
        assertEquals(0, buffer.available());

        long[] b = new long[4];
        assertEquals(0, buffer.readIfAvailable(b));
        
        long[] chunk3 = buildLongsChunk(3);
        buffer.append(chunk3);
        assertEquals(1, buffer.getSegmentCount());
        assertEquals(3, buffer.getTotalPayloadLength());
        assertEquals(27, buffer.getMaximumAvailableSpace());

        // and then let's just read it off
        assertEquals(3, buffer.read(b));
        verifyChunkPrefix(b, chunk3.length);

        // no more data; but not all is freed either
        assertEquals(0, buffer.getTotalPayloadLength());
        assertEquals(27, buffer.getMaximumAvailableSpace());
        
        // but then append two 7 long segments
        long[] chunk7 = buildLongsChunk(7);
        buffer.append(chunk7);
        buffer.append(chunk7);
        assertEquals(14, buffer.getTotalPayloadLength());

        // and third one as well
        buffer.append(chunk7);
        assertEquals(21, buffer.getTotalPayloadLength());

        // then read them all; first separately
        b = new long[8];
        assertEquals(7, buffer.read(b, 0, 7));
        verifyChunkPrefix(b, 7);
        assertEquals(14, buffer.getTotalPayloadLength());
        // then rest as longer read
        b = new long[100];
        assertEquals(14, buffer.read(b));
        verifyChunkPrefix(b, 0, 7);
        verifyChunkPrefix(b, 7, 7);

        // and now we should be empty...
        assertEquals(0, buffer.getTotalPayloadLength());
        assertTrue(buffer.isEmpty());
        // including holding on to just one segment
        assertEquals(1, buffer.getSegmentCount());

        // and shouldn't find anything else, for now
        assertEquals(0, buffer.readIfAvailable(b));
    }

    /**
     * Separate test to use single-byte methods; reproduces #18
     * (from 0.9.2).
     */
    private void _testStreamyAppendAndGet2(SegType aType) throws Exception
    {
        final MemBuffersForLongs bufs = createLongsBuffers(aType, 10, 1, 4);
        final StreamyLongsMemBuffer buffer = bufs.createStreamyBuffer(1, 4);

        assertEquals(0, buffer.getTotalPayloadLength());
        assertTrue(buffer.isEmpty());
        
        // Let's just fill with 30 bytes
        for (int i = 0; i < 30; ++i) {
            assertTrue(buffer.tryAppend(i));
        }
        assertEquals(3, buffer.getSegmentCount());
        assertEquals(30, buffer.getTotalPayloadLength());
        assertEquals(10, buffer.getMaximumAvailableSpace());

        // and then let's just read it off
        for (int i = 0; i < 30; ++i) {
            assertEquals(30 - i, buffer.available());
            long l = buffer.read();
            assertEquals(i, l);
        }
        assertEquals(0, buffer.getTotalPayloadLength());
        buffer.close();
    }
    
    /**
     * Unit test that verifies that read from empty buffer
     * works but won't return any data
     */
    private void _testStreamyReadFromEmpty(SegType aType) throws Exception
    {
        final StreamyLongsMemBuffer buffer = createLongsBuffers(aType, 1000, 1, 100).createStreamyBuffer(1, 2);
        
        long[] b = new long[4];
        // first, non-blocking version
        assertEquals(0, buffer.readIfAvailable(b));
        // then one with short timeout
        assertEquals(0, buffer.read(10L, b, 0, b.length));
    }
}
