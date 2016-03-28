/**
 *
 */
package external.zeroconf.discovery;

import external.zeroconf.discovery.tasks.resolver.ServiceResolver;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import external.zeroconf.discovery.tasks.RecordReaper;
import external.zeroconf.discovery.tasks.Responder;
import external.zeroconf.discovery.tasks.resolver.ServiceInfoResolver;
import external.zeroconf.discovery.tasks.resolver.TypeResolver;
import external.zeroconf.discovery.tasks.state.Announcer;
import external.zeroconf.discovery.tasks.state.Canceler;
import external.zeroconf.discovery.tasks.state.Prober;
import external.zeroconf.discovery.tasks.state.Renewer;

/**
 * This class is used by ZeroConf to start the various task required to run the DNS discovery. This interface is only there in order to support MANET modifications.
 * <p>
 * <b>Note: </b> This is not considered as part of the general public API of ZeroConf.
 * </p>
 *
 * @author Pierre Frisch
 */
public interface DNSTaskStarter {

    /**
     * DNSTaskStarter.Factory enable the creation of new instance of DNSTaskStarter.
     */
    final class Factory {

        private static volatile Factory                        _instance;
        private final ConcurrentMap<ZeroConf, DNSTaskStarter> _instances;

        /**
         * This interface defines a delegate to the DNSTaskStarter class to enable subclassing.
         */
        public interface ClassDelegate {

            /**
             * Allows the delegate the opportunity to construct and return a different DNSTaskStarter.
             *
             * @param jmDNSImpl
             *            jmDNS instance
             * @return Should return a new DNSTaskStarter Object.
             * @see #classDelegate()
             * @see #setClassDelegate(ClassDelegate anObject)
             */
            DNSTaskStarter newDNSTaskStarter(ZeroConf jmDNSImpl);
        }

        private static final AtomicReference<Factory.ClassDelegate> _databaseClassDelegate = new AtomicReference<Factory.ClassDelegate>();

        private Factory() {
            super();
            _instances = new ConcurrentHashMap<ZeroConf, DNSTaskStarter>(20);
        }

        /**
         * Assigns <code>delegate</code> as DNSTaskStarter's class delegate. The class delegate is optional.
         *
         * @param delegate
         *            The object to set as DNSTaskStarter's class delegate.
         * @see #classDelegate()
         * @see DNSTaskStarter.Factory.ClassDelegate
         */
        public static void setClassDelegate(Factory.ClassDelegate delegate) {
            _databaseClassDelegate.set(delegate);
        }

        /**
         * Returns DNSTaskStarter's class delegate.
         *
         * @return DNSTaskStarter's class delegate.
         * @see #setClassDelegate(ClassDelegate anObject)
         * @see DNSTaskStarter.Factory.ClassDelegate
         */
        public static Factory.ClassDelegate classDelegate() {
            return _databaseClassDelegate.get();
        }

        /**
         * Returns a new instance of DNSTaskStarter using the class delegate if it exists.
         *
         * @param jmDNSImpl
         *            jmDNS instance
         * @return new instance of DNSTaskStarter
         */
        protected static DNSTaskStarter newDNSTaskStarter(ZeroConf jmDNSImpl) {
            DNSTaskStarter instance = null;
            Factory.ClassDelegate delegate = _databaseClassDelegate.get();
            if (delegate != null) {
                instance = delegate.newDNSTaskStarter(jmDNSImpl);
            }
            return (instance != null ? instance : new DNSTaskStarterImpl(jmDNSImpl));
        }

        /**
         * Return the instance of the DNSTaskStarter Factory.
         *
         * @return DNSTaskStarter Factory
         */
        public static Factory getInstance() {
            if (_instance == null) {
                synchronized (DNSTaskStarter.Factory.class) {
                    if (_instance == null) {
                        _instance = new Factory();
                    }
                }
            }
            return _instance;
        }

        /**
         * Return the instance of the DNSTaskStarter for the ZeroConf.
         *
         * @param jmDNSImpl
         *            jmDNS instance
         * @return the DNSTaskStarter
         */
        public DNSTaskStarter getStarter(ZeroConf jmDNSImpl) {
            DNSTaskStarter starter = _instances.get(jmDNSImpl);
            if (starter == null) {
                _instances.putIfAbsent(jmDNSImpl, newDNSTaskStarter(jmDNSImpl));
                starter = _instances.get(jmDNSImpl);
            }
            return starter;
        }

    }

    final class DNSTaskStarterImpl implements DNSTaskStarter {

        private final ZeroConf _jmDNSImpl;

        /**
         * The timer is used to dispatch allOf outgoing messages of ZeroConf. It is also used to dispatch maintenance tasks for the DNS cache.
         */
        private final Timer     _timer;

        /**
         * The timer is used to dispatch maintenance tasks for the DNS cache.
         */
        private final Timer     _stateTimer;

        public static class StarterTimer extends Timer {

            // This is needed because in some case we cancel the timers before allOf the task have finished running and in some case they will try to reschedule
            private volatile boolean _cancelled;

            /**
             *
             */
            public StarterTimer() {
                super();
                _cancelled = false;
            }

            /**
             * @param isDaemon
             */
            public StarterTimer(boolean isDaemon) {
                super(isDaemon);
                _cancelled = false;
            }

            /**
             * @param name
             * @param isDaemon
             */
            public StarterTimer(String name, boolean isDaemon) {
                super(name, isDaemon);
                _cancelled = false;
            }

            /**
             * @param name
             */
            public StarterTimer(String name) {
                super(name);
                _cancelled = false;
            }

            /*
             * (non-Javadoc)
             * @see java.util.Timer#cancel()
             */
            @Override
            public synchronized void cancel() {
                if (_cancelled) return;
                _cancelled = true;
                super.cancel();
            }

            /*
             * (non-Javadoc)
             * @see java.util.Timer#schedule(java.util.TimerTask, long)
             */
            @Override
            public synchronized void schedule(TimerTask task, long delay) {
                if (_cancelled) return;
                super.schedule(task, delay);
            }

            /*
             * (non-Javadoc)
             * @see java.util.Timer#schedule(java.util.TimerTask, java.util.Date)
             */
            @Override
            public synchronized void schedule(TimerTask task, Date time) {
                if (_cancelled) return;
                super.schedule(task, time);
            }

            /*
             * (non-Javadoc)
             * @see java.util.Timer#schedule(java.util.TimerTask, long, long)
             */
            @Override
            public synchronized void schedule(TimerTask task, long delay, long period) {
                if (_cancelled) return;
                super.schedule(task, delay, period);
            }

            /*
             * (non-Javadoc)
             * @see java.util.Timer#schedule(java.util.TimerTask, java.util.Date, long)
             */
            @Override
            public synchronized void schedule(TimerTask task, Date firstTime, long period) {
                if (_cancelled) return;
                super.schedule(task, firstTime, period);
            }

            /*
             * (non-Javadoc)
             * @see java.util.Timer#scheduleAtFixedRate(java.util.TimerTask, long, long)
             */
            @Override
            public synchronized void scheduleAtFixedRate(TimerTask task, long delay, long period) {
                if (_cancelled) return;
                super.scheduleAtFixedRate(task, delay, period);
            }

            /*
             * (non-Javadoc)
             * @see java.util.Timer#scheduleAtFixedRate(java.util.TimerTask, java.util.Date, long)
             */
            @Override
            public synchronized void scheduleAtFixedRate(TimerTask task, Date firstTime, long period) {
                if (_cancelled) return;
                super.scheduleAtFixedRate(task, firstTime, period);
            }

        }

        public DNSTaskStarterImpl(ZeroConf jmDNSImpl) {
            super();
            _jmDNSImpl = jmDNSImpl;
            _timer = new StarterTimer("ZeroConf(" + _jmDNSImpl.getName() + ").Timer", true);
            _stateTimer = new StarterTimer("ZeroConf(" + _jmDNSImpl.getName() + ").State.Timer", false);
        }

        /*
         * (non-Javadoc)
         * @see DNSTaskStarter#purgeTimer()
         */
        @Override
        public void purgeTimer() {
            _timer.purge();
        }

        /*
         * (non-Javadoc)
         * @see DNSTaskStarter#purgeStateTimer()
         */
        @Override
        public void purgeStateTimer() {
            _stateTimer.purge();
        }

        /*
         * (non-Javadoc)
         * @see DNSTaskStarter#cancelTimer()
         */
        @Override
        public void cancelTimer() {
            _timer.cancel();
        }

        /*
         * (non-Javadoc)
         * @see DNSTaskStarter#cancelStateTimer()
         */
        @Override
        public void cancelStateTimer() {
            _stateTimer.cancel();
        }

        /*
         * (non-Javadoc)
         * @see DNSTaskStarter#startProber()
         */
        @Override
        public void startProber() {
            new Prober(_jmDNSImpl).start(_stateTimer);
        }

        /*
         * (non-Javadoc)
         * @see DNSTaskStarter#startAnnouncer()
         */
        @Override
        public void startAnnouncer() {
            new Announcer(_jmDNSImpl).start(_stateTimer);
        }

        /*
         * (non-Javadoc)
         * @see DNSTaskStarter#startRenewer()
         */
        @Override
        public void startRenewer() {
            new Renewer(_jmDNSImpl).start(_stateTimer);
        }

        /*
         * (non-Javadoc)
         * @see DNSTaskStarter#startCanceler()
         */
        @Override
        public void startCanceler() {
            new Canceler(_jmDNSImpl).start(_stateTimer);
        }

        /*
         * (non-Javadoc)
         * @see DNSTaskStarter#startReaper()
         */
        @Override
        public void startReaper() {
            new RecordReaper(_jmDNSImpl).start(_timer);
        }

        /*
         * (non-Javadoc)
         * @see DNSTaskStarter#startServiceInfoResolver(ServiceInfoImpl)
         */
        @Override
        public void startServiceInfoResolver(ServiceInfoImpl info) {
            new ServiceInfoResolver(_jmDNSImpl, info).start(_timer);
        }

        /*
         * (non-Javadoc)
         * @see DNSTaskStarter#startTypeResolver()
         */
        @Override
        public void startTypeResolver() {
            new TypeResolver(_jmDNSImpl).start(_timer);
        }

        /*
         * (non-Javadoc)
         * @see DNSTaskStarter#startServiceResolver(java.lang.String)
         */
        @Override
        public void startServiceResolver(String type) {
            new ServiceResolver(_jmDNSImpl, type).start(_timer);
        }

        /*
         * (non-Javadoc)
         * @see DNSTaskStarter#startResponder(DNSIncoming, int)
         */
        @Override
        public void startResponder(DNSIncoming in, int port) {
            new Responder(_jmDNSImpl, in, port).start(_timer);
        }
    }

    /**
     * Purge the general task timer
     */
    void purgeTimer();

    /**
     * Purge the state task timer
     */
    void purgeStateTimer();

    /**
     * Cancel the generals task timer
     */
    void cancelTimer();

    /**
     * Cancel the state task timer
     */
    void cancelStateTimer();

    /**
     * Start a new prober task
     */
    void startProber();

    /**
     * Start a new announcer task
     */
    void startAnnouncer();

    /**
     * Start a new renewer task
     */
    void startRenewer();

    /**
     * Start a new canceler task
     */
    void startCanceler();

    /**
     * Start a new reaper task. There is only supposed to be one reaper running at a time.
     */
    void startReaper();

    /**
     * Start a new service info resolver task
     *
     * @param info
     *            service info to resolve
     */
    void startServiceInfoResolver(ServiceInfoImpl info);

    /**
     * Start a new service type resolver task
     */
    void startTypeResolver();

    /**
     * Start a new service resolver task
     *
     * @param type
     *            service type to resolve
     */
    void startServiceResolver(String type);

    /**
     * Start a new responder task
     *
     * @param in
     *            incoming message
     * @param port
     *            incoming port
     */
    void startResponder(DNSIncoming in, int port);

}
