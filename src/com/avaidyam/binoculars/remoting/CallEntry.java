/*
 * Copyright (c) 2016 Aditya Vaidyam
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.avaidyam.binoculars.remoting;

import com.avaidyam.binoculars.Message;
import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.remoting.base.RemoteRegistry;
import com.avaidyam.binoculars.future.Future;

import java.lang.reflect.Method;

public class CallEntry<T> implements Message<T> {

    final private Method method;
    final private Object[] args;
    private Future futureCB;
    transient final private T target;    // target and target nuclei are not necessary equal. E.g. target can be callback, but calls are put onto sendingNucleus Q
    transient private Nucleus sendingNucleus; // defines the sender of this message. null in case of outside call
    transient private Nucleus targetNucleus;  // defines nuclei assignment in case target is callback
    transient private boolean onCBQueue;  // determines queue used
    transient private RemoteRegistry remoteRegistry; // remote connection call came from

    public CallEntry(T target, Method method, Object[] args, Nucleus sender, Nucleus targetNucleus, boolean isCB) {
        this.target = target;
        this.method = method;
        this.args = args;
        this.sendingNucleus = sender;
        this.targetNucleus = targetNucleus;
        this.onCBQueue = isCB;
    }

    public Nucleus getTargetNucleus() {
        return targetNucleus;
    }

    public T getTarget() {
        return target;
    }

    public Method getMethod() {
        return method;
    }

    public Object[] getArgs() {
        return args;
    }

    public Nucleus getSendingNucleus() {
        return sendingNucleus;
    }

    public void setRemoteRegistry(RemoteRegistry remoteRegistry) {
        this.remoteRegistry = remoteRegistry;
    }

    public RemoteRegistry getRemoteRegistry() {
        return remoteRegistry;
    }

//    public Message copy() {
//        return withTarget(target, true);
//    }

//    public Message withTarget(T newTarget) {
//        return withTarget(target, false);
//    }

//    public Message withTarget(T newTarget, boolean copyArgs) {
//        Nucleus targetNucleus = null;
//        if ( newTarget instanceof NucleusProxy )
//            newTarget = (T) ((NucleusProxy) newTarget).getNucleus();
//        if ( newTarget instanceof Nucleus) {
//            targetNucleus = (Nucleus) newTarget;
//        }
//        if ( copyArgs ) {
//            Object argCopy[] = new Object[args.length];
//            System.arraycopy(args, 0, argCopy, 0, args.length);
//            return new CallEntry(newTarget, method, argCopy, targetNucleus);
//        } else {
//            return new CallEntry(newTarget, method, args, targetNucleus);
//        }
//    }

    public boolean hasFutureResult() {
        return method.getReturnType() == Future.class;
    }

    public Future getFutureCB() {
        return futureCB;
    }

    public void setFutureCB(Future futureCB) {
        this.futureCB = futureCB;
    }

    @Override
    public String toString() {
        return "CallEntry{" +
                "method=" + method.getName() +
//                   ", args=" + Arrays.toString(args) +
                ", futureCB=" + futureCB +
                ", target=" + target +
                ", sendingNucleus=" + sendingNucleus +
                ", targetNucleus=" + targetNucleus +
                ", onCBQueue=" + onCBQueue +
                '}';
    }

    public boolean isCallback() {
        return onCBQueue;
    }
}
