// SPDX-License-Identifier: GPL-3.0-only
package de.agundur.eternalwatch.core;

import java.io.InterruptedIOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.logging.*;

/** One logical request, bounded I/O workers and an independent end-to-end deadline. */
public final class RequestCoordinator implements AutoCloseable {
    public enum Result { OK, STALE, INVALID, NETWORK, CANCELLED, TIMEOUT }
    public interface Work { Result run(Attempt attempt) throws Exception; }
    public interface Observer {
        default void accepted(Attempt attempt) { }
        void started(Attempt attempt);
        void finished(Attempt attempt,Result result);
    }
    private final Object lock=new Object();
    private final Executor events;
    private final Observer observer;
    private final LongSupplier clock;
    private final long budgetMillis;
    private final ThreadPoolExecutor workers=pool("ew-feed-io"),cleanup=pool("ew-feed-close");
    private final ScheduledExecutorService timer=Executors.newSingleThreadScheduledExecutor(threads("ew-feed-deadline"));
    private final ArrayDeque<Runnable> notifications=new ArrayDeque<>();
    private boolean draining;
    private Attempt active;
    private long sequence;

    public RequestCoordinator(long budgetMillis,LongSupplier clock,Executor events,Observer observer) {
        if(budgetMillis<=0)throw new IllegalArgumentException("Positive request budget required");
        this.budgetMillis=budgetMillis;this.clock=clock;this.events=events;this.observer=observer;
    }
    private static ThreadFactory threads(String name) {
        return work->{Thread thread=new Thread(work,name);thread.setDaemon(true);return thread;};
    }
    private static ThreadPoolExecutor pool(String name) {
        return new ThreadPoolExecutor(0,2,30,TimeUnit.SECONDS,new SynchronousQueue<>(),threads(name));
    }
    public boolean busy() { synchronized(lock) { return active!=null; } }
    public boolean latest(Attempt attempt) { synchronized(lock) { return sequence==attempt.id; } }
    private boolean enqueue(Runnable event) {
        notifications.add(event);
        if(draining)return false;
        draining=true;return true;
    }
    private void drain() {
        while(true) {
            Runnable event;
            synchronized(lock) {
                event=notifications.poll();
                if(event==null) { draining=false;return; }
            }
            try { event.run(); }
            catch(RuntimeException failure) { Logger.getLogger(RequestCoordinator.class.getName()).log(Level.WARNING,"Request completion handler failed",failure); }
        }
    }
    /** Busy callers join this deadline; one coalesced repeat is dispatched afterwards. */
    public void submit(Work work,Runnable complete,Runnable repeatIfBusy) {
        Attempt attempt;boolean dispatch;
        synchronized(lock) {
            if(active!=null) {
                active.completions.add(complete);
                if(repeatIfBusy!=null)active.repeat=repeatIfBusy;
                return;
            }
            attempt=new Attempt(++sequence,clock.getAsLong());
            attempt.completions.add(complete);active=attempt;
            attempt.worker=new FutureTask<>(()->{
                try {
                    Result result;
                    try { attempt.check();result=work.run(attempt); }
                    catch(Exception error) { result=Result.NETWORK; }
                    finish(attempt,result);
                } finally { attempt.closeOnWorker(); }
                return null;
            });
            attempt.timeout=timer.schedule(()->finish(attempt,Result.TIMEOUT),budgetMillis,TimeUnit.MILLISECONDS);
            dispatch=enqueue(()->{if(attempt.active())observer.started(attempt);});
        }
        // Poll throttling is recorded synchronously, even if a queued UI event is skipped.
        observer.accepted(attempt);
        if(dispatch)events.execute(this::drain);
        try { workers.execute(attempt.worker); }
        catch(RejectedExecutionException full) { finish(attempt,Result.NETWORK); }
    }
    private void dispose(Runnable action) {
        if(action==null)return;
        // disconnect() can itself block: never run it on the deadline/event thread.
        try { cleanup.execute(action); } catch(RejectedExecutionException ignored) { }
    }
    private void finish(Attempt attempt,Result result) {
        Runnable abort=null;boolean dispatch;
        synchronized(lock) {
            if(active!=attempt)return;
            if(clock.getAsLong()>=attempt.deadline)result=Result.TIMEOUT;
            active=null;attempt.timeout.cancel(false);
            if(result==Result.TIMEOUT || result==Result.CANCELLED) {
                attempt.worker.cancel(true);abort=attempt::closeOnWorker;
            }
            List<Runnable> completions=new ArrayList<>(attempt.completions);Runnable repeat=attempt.repeat;
            Result ended=result;
            // Enqueue in completion order, but run all parsing/storage/binder work outside this lock.
            dispatch=enqueue(()->{
                try { observer.finished(attempt,ended); }
                finally {
                    for(Runnable completion:completions)try { completion.run(); } catch(RuntimeException ignored) { }
                    if(repeat!=null)repeat.run();
                }
            });
        }
        dispose(abort);
        if(dispatch)events.execute(this::drain);
    }
    public final class Attempt {
        private final long id,started,deadline;
        private final List<Runnable> completions=new ArrayList<>();
        private final AtomicReference<Runnable> cleanup=new AtomicReference<>();
        private Runnable repeat;
        private Object value;
        private FutureTask<Void> worker;
        private ScheduledFuture<?> timeout;
        private Attempt(long id,long started) { this.id=id;this.started=started;this.deadline=started+budgetMillis; }
        public long startedMillis() { return started; }
        public boolean active() { synchronized(lock) { return active==this && clock.getAsLong()<deadline; } }
        public void check() throws InterruptedIOException {
            if(!active())throw new InterruptedIOException("Request ended");
        }
        public int remainingMillis(int maximum) throws InterruptedIOException {
            check();return (int)Math.max(1,Math.min(maximum,deadline-clock.getAsLong()));
        }
        public void cleanup(Runnable action) {
            cleanup.set(action);
            if(!active())dispose(this::closeOnWorker);
        }
        private void closeOnWorker() {
            Runnable close=cleanup.getAndSet(null);
            if(close!=null)close.run();
        }
        /** Only an immutable prepared value crosses the deadline lock, never application work. */
        public boolean offer(Object prepared) {
            synchronized(lock) {
                if(active!=this || clock.getAsLong()>=deadline)return false;
                value=prepared;return true;
            }
        }
        public Object value() { synchronized(lock) { return value; } }
    }
    @Override public void close() {
        Attempt pending;synchronized(lock) { pending=active; }
        if(pending!=null)finish(pending,Result.CANCELLED);
        workers.shutdownNow();cleanup.shutdownNow();timer.shutdownNow();
    }
}
