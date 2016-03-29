package test;

import com.avaidyam.binoculars.Nucleus;
import com.avaidyam.binoculars.remoting.base.ConnectibleNucleus;
import com.avaidyam.binoculars.remoting.tcp.TCPConnectible;
import com.avaidyam.binoculars.remoting.tcp.TCPPublisher;
import com.avaidyam.binoculars.future.CompletableFuture;
import com.avaidyam.binoculars.future.Future;

public class RefChain {
	
	public static class A extends Nucleus<A> {
		public Future showChain( ConnectibleNucleus b ) {
			B bref = (B) b.connect().await();
			C cref = bref.getC().await();
			String pok = cref.hello("POK").await();
			System.out.println("received "+pok);
			return new CompletableFuture(null);
		}
	}
	
	public static class B extends Nucleus<A> {
		C c;
		public void init(ConnectibleNucleus<Nucleus> connectable) {
			connectable.connect().then((Nucleus c) -> this.c = (C)c);
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
		
		ConnectibleNucleus cConnect = new TCPConnectible(C.class, "localhost", 4003);
		ConnectibleNucleus bConnect = new TCPConnectible(B.class, "localhost", 4002);
		
		b.init(cConnect);
		Thread.sleep(500); // don't program like this, init should return promise ..
		
		a.showChain(bConnect).await();
		
		a.stop();
		b.stop();
		c.stop();
		System.exit(0);
	}
}