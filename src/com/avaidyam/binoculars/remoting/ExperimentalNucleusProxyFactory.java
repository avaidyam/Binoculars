package com.avaidyam.binoculars.remoting;


import com.avaidyam.binoculars.Domain;
import com.avaidyam.binoculars.Nucleus;
import javassist.Modifier;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.NamingStrategy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.*;
import org.nustaq.serialization.util.FSTUtil;

import java.io.Externalizable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static net.bytebuddy.description.modifier.Visibility.PUBLIC;
import static net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default.WRAPPER;
import static net.bytebuddy.matcher.ElementMatchers.*;

public class ExperimentalNucleusProxyFactory {
    HashMap<Class, Class> generatedProxyClasses = new HashMap<>();

    public ExperimentalNucleusProxyFactory() {}

    private static <T extends Nucleus> T setTarget(T instance, T target) throws Exception {
        Field f = instance.getClass().getField("__target");
        f.setAccessible(true);
        f.set(instance, target);
        return instance;
    }

    private static <T extends Nucleus> T getTarget(T instance) throws Exception {
        Field f = instance.getClass().getField("__target");
        //noinspection unchecked
        return (T)f.get(instance);
    }

    @SuppressWarnings("unchecked")
    public <T extends Nucleus> T instantiateProxy(T target) throws Exception {
        Class<? extends Nucleus> targetClass = target.getClass();
        if (!Modifier.isPublic(targetClass.getModifiers()))
            throw new RuntimeException("Nucleus class must be public:" + targetClass.getName());
        if (targetClass.isAnonymousClass())
            throw new RuntimeException("Anonymous classes can't be Nuclei:" + targetClass.getName());
        if (targetClass.isMemberClass() && !Modifier.isStatic(targetClass.getModifiers()))
            throw new RuntimeException("Only STATIC inner classes can be Nuclei:" + targetClass.getName());

        Class<?> proxyClass = null;
        if (!generatedProxyClasses.containsKey(targetClass)) {
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
                            .and(not(isAnnotatedWith(Domain.CallerSide.class)))
                            .and(not(isAbstract()))
                            .and(not(isNative()))
                            .and(not(isFinal()))
                            .and(not(isStatic()))
                            .and(not(named("self")))
                            .and(not(isDeclaredBy(Object.class)))
                            .and(not(isDeclaredBy(Nucleus.class).and(not(
                                    named("getSubMonitorables").or(named("getReport")).or(named("ask"))
                                    .or(named("tell")).or(named("__unpublish")).or(named("__republished"))
                                    .or(named("ping")).or(named("__submit")).or(named("exec")).or(named("asyncStop"))
                                    .or(named("receive")).or(named("receive")).or(named("complete")).or(named("close"))
                                    .or(named("spore")).or(named("init")).or(named("deinit")))))))
                    .intercept(MethodDelegation.to(ProxyInterceptor.class))
                    .method(isNative().or(isFinal()).or(isStatic())
                            .and(not(named("getNucleus")).or(named("delayed")).or(named("exec")))
                            .and(not(isDeclaredBy(Object.class))))
                    .intercept(MethodDelegation.to(NonProxyInterceptor.class))
                    .make()
                    .load(getClass().getClassLoader(), WRAPPER)
                    .getLoaded();

            generatedProxyClasses.put(targetClass, proxyClass);
        } else proxyClass = generatedProxyClasses.get(targetClass);

        // Use sun.misc.Unsafe to avoid calling the constructor.
        T instance = null;
        if (FSTUtil.unFlaggedUnsafe != null)
            instance = (T)FSTUtil.unFlaggedUnsafe.allocateInstance(proxyClass);
        else instance = (T)proxyClass.newInstance();
        return setTarget(instance, target);
    }




    public static class ProxyInterceptor {
        @RuntimeType
        @SuppressWarnings("unused")
        public static Object intercept(@AllArguments Object[] allArguments, @This Nucleus _this, @Origin Method method) throws Exception {

            // TODO: This needs to be generated...
            for (int i = 0; i < allArguments.length; i++) {
                List<Annotation> annotations = Arrays.asList(allArguments[i].getClass().getDeclaredAnnotations());
                for (Annotation a : annotations) {
                    if (a.annotationType().equals(Domain.InThread.class)) {
                        Nucleus sender = Nucleus.sender.get();
                        if (sender != null) {
                            allArguments[i] = sender.__scheduler.inThread(sender.__self, allArguments[i]);
                        }
                        break;
                    }
                }
            }

            boolean isCallbackCall = method.getAnnotation(Domain.SignalPriority.class) != null;
            Object result = getTarget(_this).__enqueueCall(getTarget(_this), method.getName(), allArguments, isCallbackCall);
            return method.getReturnType().cast(result);
        }
    }

    public static class NonProxyInterceptor {
        @RuntimeType
        @SuppressWarnings("unused")
        public static Object intercept(@Origin Method method) throws Exception {
            throw new RuntimeException("Can't invoke non-public Nuclei methods " + method.getName());
        }
    }
}
