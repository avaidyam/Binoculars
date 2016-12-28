package com.avaidyam.binoculars.scheduler;

import com.avaidyam.binoculars.Message;

import java.util.Queue;
import java.util.concurrent.PriorityBlockingQueue;

/*
Nucleus -> PriorityQueue
	.enqueue() = queue.push(invoke) AND
	.enqueue() = dispatcher.push(WeakReference(sender), invoke)

Dispatcher -> LinkedBlockingQueue
	.poll() = queue.pop().pop().invoke() OR
	.poll() = queue.pop() is STOP ? .stop()
	.stop() = .shutdown()

PriorityBlockingQueue<WorkItem> producers[] = new PriorityBlockingQueue<WorkItem>[NUM_PRODUCERS];
BlockingQueue<BlockingQueue<WorkItem>> producerProducer = new BlockingQueue<BlockingQueue<WorkItem>>();

* sender dies?
* sender dealloc?
* sender close?

BlockingScheduler uses above method
ExecutorScheduler uses ExecutorCompletionService
*/
public class ExperimentalDispatcher extends Thread {

    public PriorityBlockingQueue<Queue<Message>> queue = new PriorityBlockingQueue<>();

    @Override
    public void run() {
        try {
            for (Queue<Message> current; (current = queue.take()) != null;) {
                Message message = current.poll();

            }
        } catch(InterruptedException interrupted) {
            // Shut down.
        }
    }
}
