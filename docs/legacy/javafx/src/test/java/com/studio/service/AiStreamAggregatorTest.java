package com.studio.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The live preview must stay cheap and truthful: it shows only the newest text of one stream, never
 * interleaves two agents, and a JSON stream contributes progress without pasting raw JSON.
 */
class AiStreamAggregatorTest {

    @Test
    void keepsOnlyTheNewestTailOfOneSource() {
        AiStreamAggregator aggregator = new AiStreamAggregator(10);
        aggregator.append("SpecMiner", "0123456789");
        aggregator.append("SpecMiner", "ABCDE");

        AiStreamAggregator.Snapshot snapshot = aggregator.snapshot();

        assertEquals(1, snapshot.textSources());
        assertEquals("SpecMiner", snapshot.sourceName());
        assertEquals("56789ABCDE", snapshot.preview(), "the buffer must be trimmed from the front");
        assertEquals(15, snapshot.receivedChars(), "trimming must not lose the progress total");
    }

    @Test
    void countedSourceReportsProgressWithoutText() {
        AiStreamAggregator aggregator = new AiStreamAggregator();
        aggregator.count("{\"cases\":[");
        aggregator.count("]}");

        AiStreamAggregator.Snapshot snapshot = aggregator.snapshot();

        assertEquals(0, snapshot.textSources(), "a JSON stream is counted, never previewed");
        assertEquals(12, snapshot.receivedChars());
        assertTrue(snapshot.preview().isEmpty());
        assertTrue(snapshot.hasContent());
    }

    @Test
    void twoConcurrentStreamsHideTheInterleavedTail() {
        AiStreamAggregator aggregator = new AiStreamAggregator();
        aggregator.append("Alpha", "nội dung alpha");
        aggregator.append("Beta", "nội dung beta");

        AiStreamAggregator.Snapshot snapshot = aggregator.snapshot();

        assertEquals(2, snapshot.textSources());
        assertTrue(snapshot.preview().isEmpty(),
                "two unrelated answers must not be concatenated into one preview");
        assertEquals("nội dung alphanội dung beta".length(), snapshot.receivedChars());
    }

    @Test
    void finishingOneOfTwoConcurrentStreamsRestoresTheTail() {
        AiStreamAggregator aggregator = new AiStreamAggregator();
        aggregator.append("Alpha", "alpha đang viết");
        aggregator.append("Beta", "beta đang viết");
        assertTrue(aggregator.snapshot().preview().isEmpty(),
                "two active streams hide the interleaved tail");

        aggregator.completeSource("Beta");

        AiStreamAggregator.Snapshot snapshot = aggregator.snapshot();
        assertEquals(1, snapshot.textSources());
        assertEquals("Alpha", snapshot.sourceName());
        assertEquals("alpha đang viết", snapshot.preview(),
                "the remaining agent's answer becomes visible again");
        assertEquals("alpha đang viếtbeta đang viết".length(), snapshot.receivedChars(),
                "completing a source keeps the progress total");
    }

    @Test
    void resetStartsANewTurnFromScratch() {
        AiStreamAggregator aggregator = new AiStreamAggregator();
        aggregator.append("Alpha", "lượt trước");
        aggregator.reset();

        AiStreamAggregator.Snapshot snapshot = aggregator.snapshot();

        assertEquals(0, snapshot.textSources());
        assertEquals(0, snapshot.receivedChars());
        assertFalse(snapshot.hasContent());
    }

    @Test
    void concurrentAppendsNeverThrowOrCorruptTheBuffer() throws Exception {
        AiStreamAggregator aggregator = new AiStreamAggregator(200);
        int workers = 4;
        int piecesPerWorker = 200;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);

        for (int worker = 0; worker < workers; worker++) {
            String source = worker % 2 == 0 ? "Alpha" : "Beta";
            Thread thread = new Thread(() -> {
                try {
                    start.await(2, TimeUnit.SECONDS);
                    for (int i = 0; i < piecesPerWorker; i++) {
                        aggregator.append(source, "x");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            thread.setDaemon(true);
            thread.start();
        }
        start.countDown();

        assertTrue(done.await(5, TimeUnit.SECONDS), "every writer must finish");
        assertEquals(workers * piecesPerWorker, aggregator.snapshot().receivedChars());
        assertTrue(aggregator.snapshot().preview().length() <= 200, "the cap holds under contention");
    }
}
