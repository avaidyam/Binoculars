package test;

import org.junit.Test;
import com.binoculars.stream.*;
import com.binoculars.nuclei.remoting.tcp.*;

import java.io.Serializable;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

public class Java8Streams {

	public static class MyEvent implements Serializable {
		int num;
		double price;
		String name;

		public MyEvent(int num, double price, String name) {
			this.num = num;
			this.price = price;
			this.name = name;
		}
	}

	public <T> int remoteJava8Streams(Function<MyEvent, T> doStuff, int port) throws InterruptedException {
		AtomicInteger validCount = new AtomicInteger(-1);
		KxReactiveStreams.get()
				.produce(IntStream.range(0, 5_000_000)
						.mapToObj(i -> new MyEvent(i, Math.random(), "Hello " + i)))
				.serve(new TCPPublisher().port(port));

		CountDownLatch latch = new CountDownLatch(1);

		KxReactiveStreams.get()
				.connect(MyEvent.class, new TCPConnectable().host("localhost").port(port))
				.stream(stream -> {
					long count = 0;
					try {
						count = stream.map(doStuff::apply).count();
					} finally {
						System.out.println("Count:" + count);
						validCount.set((int)count);
						latch.countDown();
					}
				});

		latch.await();
		return validCount.get();
	}

	@Test
	public void java8streamsRemote() throws InterruptedException {
		int res = remoteJava8Streams(x -> x, 8123);
		System.out.println("res: " + res);
		assert res == 5_000_000;
	}

	@Test
	public void java8streamsRemoteAndCancel() throws InterruptedException {
		int res = remoteJava8Streams(x -> {
			if(x.num == 2_000_000)
				throw CancelException.Instance;
			return x;
		}, 8124);
		assert res == 0;
	}

	@Test //@org.junit.Ignore // just manual
	public void remoteJava8EndlessStreams() throws InterruptedException {
		EventSink<Date> sink = new EventSink<>();
		sink.serve(new TCPPublisher().port(8125));

		Consumer<String> rtc = (tag) -> KxReactiveStreams.get()
				.connect(Date.class, new TCPConnectable().host("localhost").port(8125))
				.stream(stream -> stream.forEach(date -> {
					System.out.println(tag + " " + date);

					if (Math.random() < 0.1)
						throw CancelException.Instance;
				}));

		rtc.accept("c1");
		rtc.accept("c2");
		rtc.accept("c3");

		for(int i = 0; i < 1000; i++) {
			while(!sink.offer(new Date()))
				Thread.sleep(1);
			Thread.sleep(1000);
		}
	}
}