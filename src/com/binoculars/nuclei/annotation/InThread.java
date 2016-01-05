package com.binoculars.nuclei.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PARAMETER})

/**
 * parameters tagged as callback get wrapped by 'Nucleus.Callback' automatically. Calls on these object are
 * executed in the callers thread (enqueued to the calling Nucleus queue)
 * automatically applied for callback + futures
 *
 */
public @interface InThread {
}
