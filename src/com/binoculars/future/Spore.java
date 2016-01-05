package com.binoculars.future;

import com.binoculars.nuclei.Nucleus;
import org.nustaq.serialization.annotations.AnonymousTransient;

import java.io.Serializable;

/**
 * A Spore is sent to a foreign nuclei executes on its data and sends results back to caller
 *
 * The original nuclei model defines a pure asynchronous message passing system. It misses one important pattern.

 In distributed systems its cheaper to send a snipped of code and let it work on the data in a remote nuclei instead of streaming the a data set over the wire to the receiving nuclei to do some computation.

 Constraint A spore must not access fields of its enclosing class as the hidden this$0 reference is automatically cleared during serialization. This is not a real constraint, you can capture any state from the outer class as shown above in the initializer block of the Spore which is executed on caller side. Note you can introduce parallelism accidentally by accessing the parent class from an anonymous spore.

 Update since kontraktor 2.0-beta3 its recommended to listen to remote results in a different way like:

 store.$stream(
 new Spore() {
 // REMOTE method executed remotely/in foreign nuclei, receiver side
 // you can access any state captured (held by the spore object directly)
 public void remote(Object input) {
 [...]
 }
 }.then( (result, error) -> {
 // LOCAL this method is invoked on sender side and receives
 // the results evaluated remotely by the spore
 });
 );
 This avoids nasty issues caused by accidental serialization of local state/lambdas.
 *
 */
@AnonymousTransient
public abstract class Spore<I, O> implements Serializable {

    transient protected boolean finished;
    transient private Signal<O> localSignal;
    transient private CompletableFuture finSignal = new CompletableFuture();

    // Wrap the core functionality of the class in a wrapper.
    private Signal<O> wrapper = new SignalWrapper<>(Nucleus.sender.get(), (r, e) -> {
        if (Signal.isComplete(e)) {
            this.finSignal.complete();
        } else if (this.localSignal != null) {
            this.localSignal.complete(r, e);
        } else System.err.println("No callback assigned prior to sending Spore!");
    });

    /**
     *
     */
    public Spore() {}

    /**
     * Remotely executed code.
     *
     * @param input
     */
    protected abstract void remote(I input);

    public final void doRemote(I input) {
        remote(input);
    }

    /**
     * local. Register at sending side and will recieve data streamed back from remote.
     *
     * @param cb
     * @return a future triggered
     */
    public Spore<I, O> forEach(Signal<O> cb) {
        if (this.localSignal != null)
            throw new RuntimeException("forEach callback handler can only be set once.");
        this.localSignal = cb;
        return this;
    }

    public Spore<I, O> onFinish(Runnable r) {
        this.finSignal.then(r);
        return this;
    }

    /**
     * to be called at remote side
     * when using streaming to deliver multiple results, call this in order to signal no further
     * results are expected.
     */
    public void finished() {
        // signal finish of execution, so remoting can clean up callback id mappings
        // override if always single result or finish can be emitted by the remote method
        // note one can send FINSILENT to avoid the final message to be visible to receiver callback/spore
        wrapper.complete(null, null);
        finished = true;
    }

    /**
     * note that sending an error implicitely will close the backstream.
     *
     * @param err
     */
    protected void streamError(Object err) {
        wrapper.complete(null, err);
    }

    /**
     *
     * @param result
     */
    protected void stream(O result) {
        wrapper.complete(result, Signal.CONT);
    }

    /**
     * to be read at remote side in order to decide wether to stop e.g. iteration.
     *
     * @return
     */
    public boolean isFinished() {
        return finished;
    }
}
