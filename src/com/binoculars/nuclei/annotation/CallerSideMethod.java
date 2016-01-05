package com.binoculars.nuclei.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})

/**
 *
 * Specifies this method is a utility processed on client side / inside sender thread.
 * e.g.
 *
 * class .. extends Nucleus {
 *     public void message( long timeStamp, String stuff ) {..}
 *
 *     // just an utility executed inside calling thread
 *     @CallerSideMethod public void message( String stuff ) {
 *         message( System.currentTimeMillis(), stuff );
 *     }
 *
 *     @CallerSideMethod public int getId() {
 *         // get "real" nuclei impl
 *         return getNucleus().id; // concurrent access !! final, volatile and locks might be required
 *     }
 *
 * }
 *
 * Note those method cannot access local state of the nuclei, they just might invoke methods as they
 * are called on the proxy object (Nucleus Ref).
 *
 * If one urgently needs to access local nuclei state synchronous, its possible to obtain the real nuclei instance by calling getNucleus().
 * Note that multithreading primitives might required then, as internal nuclei state is accessed concurrently
 * this way.
 *
 * WARNING: @CallersideMethod's cannot be invoked from remote (via network)
 *
 */
public @interface CallerSideMethod {
}