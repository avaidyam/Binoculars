package com.avaidyam.binoculars.internal;

import com.avaidyam.binoculars.Domain;
import com.avaidyam.binoculars.Nucleus;
import javassist.Modifier;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.NamingStrategy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import org.nustaq.serialization.util.FSTUtil;

import java.io.Externalizable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

import static net.bytebuddy.description.modifier.Visibility.PUBLIC;
import static net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default.WRAPPER;
import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * The NucleusProxifier generates the proxy instance corresponding to an underlying
 * target. That is, the generated proxy intercepts all method calls and queues them
 * for the underlying target's invocation later.
 */
public class NucleusProxifier {
    public NucleusProxifier() {}

    /**
     * When generating proxy classes, cache them so we don't generate colliding
     * classes (i.e. if they need to be remoted).
     */
    private HashMap<Class, Class> _pregenerated = new HashMap<>();

    /**
     * Set the proxy instance's underlying target. This is the actual object that
     * will receive all the queued method invocations made by the proxy.
     *
     * @param instance the proxy instance
     * @param target the underlying target
     * @param <T> the type of the nucleus proxied
     * @return the proxy instance
     * @throws Exception
     */
    private static <T extends Nucleus> T setTarget(T instance, T target) throws Exception {
        Field f = instance.getClass().getField("__target");
        f.setAccessible(true);
        f.set(instance, target);
        return instance;
    }

    /**
     * Get the proxy instance's underlying target.  This is the actual object that
     * will receive all the queued method invocations made by the proxy.
     *
     * @param instance the proxy instance
     * @param <T> the type of the nucleus proxied
     * @return the underlying target
     * @throws Exception
     */
    private static <T extends Nucleus> T getTarget(T instance) throws Exception {
        Field f = instance.getClass().getField("__target");
        //noinspection unchecked
        return (T)f.get(instance);
    }

    /**
     * Generate and instantiate a proxy/wrapper for the given target nucleus.
     *
     * @param target the non-proxy nucleus instance
     * @param <T> the type of the nucleus to proxy
     * @return a proxied nucleus
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public <T extends Nucleus> T instantiateProxy(T target) throws Exception {
        Class<? extends Nucleus> targetClass = target.getClass();

        // The nucleus class MUST be public, non-inner if static, and non-anonymous.
        if (!Modifier.isPublic(targetClass.getModifiers()))
            throw new RuntimeException("Nucleus class must be public:" + targetClass.getName());
        if (targetClass.isAnonymousClass())
            throw new RuntimeException("Anonymous classes can't be Nuclei:" + targetClass.getName());
        if (targetClass.isMemberClass() && !Modifier.isStatic(targetClass.getModifiers()))
            throw new RuntimeException("Only static inner classes can be Nuclei:" + targetClass.getName());

        // If we have a cached copy of the nucleus class, use that instead
        // of re-generating new nuclei proxies.
        Class<?> proxyClass = null;
        if (!_pregenerated.containsKey(targetClass)) {
            proxyClass = new ByteBuddy()
                    .with(new NamingStrategy.AbstractBase() {
                        protected String name(TypeDescription t) {
                            return t.getName() + "_NucleusProxy";
                        }
                    })
                    .subclass(targetClass)
                    .implement(Externalizable.class, Nucleus.NucleusProxy.class)
                    .defineField("__target", targetClass, PUBLIC)
                    .method(returns(Nucleus.class).and(isPublic()).and(named("getNucleus")))
                    .intercept(FieldAccessor.ofField("__target"))
                    .method(isPublic()
                            .and(not(isAbstract()))
                            .and(not(isNative()))
                            .and(not(isFinal()))
                            .and(not(isStatic()))
                            .and(not(named("self")))
                            .and(isAnnotatedWith(Domain.Export.class)))
                    .intercept(MethodDelegation.to(ProxyInterceptor.class))
                    .make()
                    .load(getClass().getClassLoader(), WRAPPER)
                    .getLoaded();

            _pregenerated.put(targetClass, proxyClass);
        } else proxyClass = _pregenerated.get(targetClass);

        // Use sun.misc.Unsafe to avoid calling the constructor.
        T instance = null;
        if (FSTUtil.unFlaggedUnsafe != null)
            instance = (T)FSTUtil.unFlaggedUnsafe.allocateInstance(proxyClass);
        else instance = (T)proxyClass.newInstance();
        return setTarget(instance, target);
    }

    /**
     * Methods intercepted by the ProxyInterceptor will be enqueued on the target and
     * executed asynchronously. If an argument has an InThread annotation, it will be
     * evaluated first, then the method will be enqueued.
     */
    public static class ProxyInterceptor {
        @RuntimeType
        @SuppressWarnings("unused")
        public static Object intercept(@AllArguments Object[] allArguments, @This Nucleus _this, @Origin Method method) throws Exception {

            // TODO: This needs to be generated...
            Annotation[][] params = method.getParameterAnnotations();
            for (int i = 0; i < params.length; i++) {
                for (Annotation a : params[i]) {
                    if (a.annotationType().equals(Domain.InThread.class) && allArguments[i] != null) {
                        Nucleus sender = Nucleus.sender.get();
                        if (sender != null)
                            allArguments[i] = sender.__scheduler.inThread(sender.__self, allArguments[i]);
                        break;
                    }
                }
            }

            boolean isCallbackCall = method.getAnnotation(Domain.SignalPriority.class) != null;
            Object result = getTarget(_this).__enqueueCall(getTarget(_this), method.getName(), allArguments, isCallbackCall);
            return method.getReturnType().cast(result);
        }
    }

    /**
     * Methods intercepted by the NonProxyInterceptor cannot be invoked on the proxy.
     */
    public static class NonProxyInterceptor {
        @RuntimeType
        @SuppressWarnings("unused")
        public static Object intercept(@Origin Method method) throws Exception {
            throw new RuntimeException("Can't invoke non-public Nuclei methods " + method.getName());
        }
    }
}
