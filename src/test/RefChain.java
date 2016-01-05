package test;

import com.binoculars.nuclei.Nucleus;
import com.binoculars.nuclei.remoting.base.ConnectableNucleus;
import com.binoculars.nuclei.remoting.tcp.TCPConnectable;
import com.binoculars.nuclei.remoting.tcp.TCPPublisher;
import com.binoculars.future.CompletableFuture;
import com.binoculars.future.Future;

public class RefChain {
	
	public static class A extends Nucleus<A> {
		public Future showChain( ConnectableNucleus b ) {
			B bref = (B) b.connect().await();
			C cref = bref.getC().await();
			String pok = cref.hello("POK").await();
			System.out.println("received "+pok);
			return new CompletableFuture(null);
		}
	}
	
	public static class B extends Nucleus<A> {
		C c;
		public void init(ConnectableNucleus connectable) {
			connectable.connect().then((Object c) -> this.c = (C)c);
		}
		public Future<C> getC() {
			return new CompletableFuture<>(c);
		}
	}
	
	public static class C extends Nucleus<A> {
		public Future<String> hello(String s) {
			return new CompletableFuture<>("Hello:"+s);
		}
	}
	
	public static void main(String[] args) throws InterruptedException {
		
		// though a,b,c run inside single process use remote refs to interconnect
		A a = Nucleus.of(A.class);
		B b = Nucleus.of(B.class);
		C c = Nucleus.of(C.class);
		
		new TCPPublisher(a, 4001).publish();
		new TCPPublisher(b, 4002).publish();
		new TCPPublisher(c, 4003).publish();
		
		ConnectableNucleus cConnect = new TCPConnectable(C.class, "localhost", 4003);
		ConnectableNucleus bConnect = new TCPConnectable(B.class, "localhost", 4002);
		
		b.init(cConnect);
		Thread.sleep(500); // don't program like this, init should return promise ..
		
		a.showChain(bConnect).await();
		
		a.stop();
		b.stop();
		c.stop();
		System.exit(0);
	}
}