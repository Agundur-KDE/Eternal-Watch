// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch.core;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class RequestCoordinatorTest {
    private static long now() { return System.nanoTime()/1_000_000; }
    private static RequestCoordinator queue(long timeout,List<RequestCoordinator.Result> results) { return queue(timeout,results,new AtomicInteger()); }
    private static RequestCoordinator queue(long timeout,List<RequestCoordinator.Result> results,AtomicInteger cache) {
        return new RequestCoordinator(timeout,RequestCoordinatorTest::now,Runnable::run,new RequestCoordinator.Observer() {
            public void started(RequestCoordinator.Attempt attempt) { }
            public void finished(RequestCoordinator.Attempt attempt,RequestCoordinator.Result result) {
                if(result==RequestCoordinator.Result.OK && attempt.value() instanceof Integer value)cache.set(value);
                results.add(result);
            }
        });
    }
    private static void await(CountDownLatch latch) throws InterruptedException { assertTrue("bounded completion",latch.await(3,TimeUnit.SECONDS)); }
    private static void uninterruptible(CountDownLatch latch) {
        boolean done=false;
        while(!done)try { latch.await();done=true; } catch(InterruptedException ignored) { }
    }

    @Test public void slowDrippingHeadersCannotHoldCompletionOrTheNextRequest() throws Exception {
        List<RequestCoordinator.Result> results=Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Socket> peer=new AtomicReference<>();CountDownLatch headers=new CountDownLatch(1);
        AtomicInteger published=new AtomicInteger();
        try(ServerSocket server=new ServerSocket(0);RequestCoordinator requests=queue(300,results,published)) {
            server.setSoTimeout(2000);
            Thread source=new Thread(()->{
                try(Socket socket=server.accept()) {
                    peer.set(socket);OutputStream out=socket.getOutputStream();
                    out.write("HTTP/1.1 200 OK\r\nX-Slow: ".getBytes(java.nio.charset.StandardCharsets.US_ASCII));out.flush();headers.countDown();
                    for(int i=0;i<200;i++) { out.write('x');out.flush();Thread.sleep(20); }
                    out.write("\r\nContent-Length: 2\r\n\r\nok".getBytes(java.nio.charset.StandardCharsets.US_ASCII));out.flush();
                } catch(Exception expectedOnCancellation) { }
            });source.setDaemon(true);source.start();
            AtomicInteger completions=new AtomicInteger();CountDownLatch complete=new CountDownLatch(1);
            long start=now();
            requests.submit(attempt->{
                HttpURLConnection connection=(HttpURLConnection)new URL("http://127.0.0.1:"+server.getLocalPort()).openConnection();
                attempt.cleanup(connection::disconnect);connection.setReadTimeout(150);
                try { connection.getResponseCode();attempt.offer(1);return RequestCoordinator.Result.OK; }
                finally { connection.disconnect(); }
            },()->{completions.incrementAndGet();complete.countDown();},null);
            await(headers);await(complete);
            assertTrue("overall budget includes headers",now()-start<2000);
            assertEquals(RequestCoordinator.Result.TIMEOUT,results.get(0));assertEquals(0,published.get());
            CountDownLatch recovered=new CountDownLatch(1);
            requests.submit(attempt->{assertTrue(attempt.offer(1));return RequestCoordinator.Result.OK;},recovered::countDown,null);
            await(recovered);assertEquals(1,published.get());assertEquals(1,completions.get());
            assertFalse(requests.busy());
        } finally { if(peer.get()!=null)peer.get().close(); }
    }

    @Test public void lateWorkCannotOverwriteFreshDataOrCompleteTwice() throws Exception {
        List<RequestCoordinator.Result> results=Collections.synchronizedList(new ArrayList<>());
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1),lateEnded=new CountDownLatch(1),expired=new CountDownLatch(1);
        AtomicInteger cache=new AtomicInteger(),completions=new AtomicInteger();
        try(RequestCoordinator requests=queue(200,results,cache)) {
            requests.submit(attempt->{
                started.countDown();uninterruptible(release);
                assertFalse(attempt.offer(-1));lateEnded.countDown();return RequestCoordinator.Result.OK;
            },()->{completions.incrementAndGet();expired.countDown();},null);
            await(started);await(expired);
            CountDownLatch fresh=new CountDownLatch(1);
            requests.submit(attempt->{attempt.offer(42);return RequestCoordinator.Result.OK;},fresh::countDown,null);
            await(fresh);release.countDown();await(lateEnded);
            assertEquals(42,cache.get());assertEquals(1,completions.get());
            assertEquals(List.of(RequestCoordinator.Result.TIMEOUT,RequestCoordinator.Result.OK),results);
        } finally { release.countDown(); }
    }

    @Test public void overlappingReconnectsCoalesceAndBusyCallersWaitForCompletion() throws Exception {
        List<RequestCoordinator.Result> results=Collections.synchronizedList(new ArrayList<>());
        CountDownLatch release=new CountDownLatch(1),started=new CountDownLatch(1),first=new CountDownLatch(1),second=new CountDownLatch(1);
        AtomicInteger repeats=new AtomicInteger(),joined=new AtomicInteger();
        try(RequestCoordinator requests=queue(2000,results)) {
            requests.submit(attempt->{started.countDown();release.await();return RequestCoordinator.Result.CANCELLED;},first::countDown,null);
            await(started);
            Runnable reconnect=()->{
                repeats.incrementAndGet();requests.submit(attempt->RequestCoordinator.Result.OK,second::countDown,null);
            };
            for(int i=0;i<20;i++)requests.submit(attempt->{fail("joined request must not run");return null;},joined::incrementAndGet,reconnect);
            assertEquals(0,joined.get());release.countDown();await(first);await(second);
            assertEquals(20,joined.get());assertEquals(1,repeats.get());
            assertEquals(List.of(RequestCoordinator.Result.CANCELLED,RequestCoordinator.Result.OK),results);
        } finally { release.countDown(); }
    }

    @Test public void exhaustedIoSlotsFailBoundedlyInsteadOfGrowingThreadsOrQueue() throws Exception {
        List<RequestCoordinator.Result> results=Collections.synchronizedList(new ArrayList<>());
        CountDownLatch release=new CountDownLatch(1);
        try(RequestCoordinator requests=queue(150,results)) {
            for(int i=0;i<2;i++) {
                CountDownLatch entered=new CountDownLatch(1),complete=new CountDownLatch(1);
                requests.submit(attempt->{entered.countDown();uninterruptible(release);return RequestCoordinator.Result.OK;},complete::countDown,null);
                await(entered);await(complete);
            }
            CountDownLatch rejected=new CountDownLatch(1);
            requests.submit(attempt->{fail("no third I/O worker");return null;},rejected::countDown,null);
            await(rejected);assertFalse(requests.busy());
            assertEquals(List.of(RequestCoordinator.Result.TIMEOUT,RequestCoordinator.Result.TIMEOUT,RequestCoordinator.Result.NETWORK),results);
        } finally { release.countDown(); }
    }

    @Test public void elapsedBudgetPreventsPublishEvenBeforeTimerThreadRuns() throws Exception {
        AtomicLong clock=new AtomicLong(1000);List<RequestCoordinator.Result> results=new CopyOnWriteArrayList<>();
        try(RequestCoordinator requests=new RequestCoordinator(2000,clock::get,Runnable::run,new RequestCoordinator.Observer() {
            public void started(RequestCoordinator.Attempt attempt) { }
            public void finished(RequestCoordinator.Attempt attempt,RequestCoordinator.Result result) { results.add(result); }
        })) {
            CountDownLatch done=new CountDownLatch(1);
            requests.submit(attempt->{clock.set(3000);assertFalse(attempt.offer("late cache value"));return RequestCoordinator.Result.OK;},done::countDown,null);
            await(done);assertEquals(List.of(RequestCoordinator.Result.TIMEOUT),results);
        }
    }

    @Test public void delayedUiKeepsAcceptedDataAndCompletionOrderEvenWhenNewerRequestFails() throws Exception {
        BlockingQueue<Runnable> ui=new LinkedBlockingQueue<>();
        List<String> events=new ArrayList<>();AtomicInteger accepted=new AtomicInteger(),started=new AtomicInteger();
        AtomicReference<RequestCoordinator> owner=new AtomicReference<>();
        try(RequestCoordinator requests=new RequestCoordinator(2000,RequestCoordinatorTest::now,ui::add,new RequestCoordinator.Observer() {
            public void accepted(RequestCoordinator.Attempt attempt) { accepted.incrementAndGet(); }
            public void started(RequestCoordinator.Attempt attempt) { started.incrementAndGet(); }
            public void finished(RequestCoordinator.Attempt attempt,RequestCoordinator.Result result) {
                if(result==RequestCoordinator.Result.OK)events.add("data:"+attempt.value());
                if(owner.get().latest(attempt))events.add("status:"+result);
            }
        })) {
            owner.set(requests);CountDownLatch prepared=new CountDownLatch(1);
            requests.submit(attempt->{assertTrue(attempt.offer(42));prepared.countDown();return RequestCoordinator.Result.OK;},()->events.add("complete:A"),null);
            await(prepared);long deadline=now()+1500;
            while(requests.busy() && now()<deadline)Thread.sleep(1);
            assertFalse(requests.busy());
            requests.submit(attempt->RequestCoordinator.Result.NETWORK,()->events.add("complete:B"),null);
            while(requests.busy() && now()<deadline)Thread.sleep(1);
            assertFalse(requests.busy());assertEquals(2,accepted.get());assertEquals(0,started.get());
            Runnable pending=ui.poll(1,TimeUnit.SECONDS);assertNotNull(pending);pending.run();
            assertEquals(List.of("data:42","complete:A","status:NETWORK","complete:B"),events);
            assertEquals(0,started.get());assertTrue(ui.isEmpty());
        }
    }
}
