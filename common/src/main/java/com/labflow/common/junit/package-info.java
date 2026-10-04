/**
 * JUnit 5 support shared by both check modules: the labels that pick which checks run.
 *
 * <p>Each label is a composed annotation over JUnit's {@code @Tag}, so a typo is a compile
 * error rather than a silently empty run. The tag names themselves live in {@link
 * com.labflow.common.junit.Labels}.
 *
 * <p>It also holds {@link com.labflow.common.junit.CheckNameInterceptor}, which puts the running
 * check's name into the log context so every log line can say which check it belongs to.
 *
 * <p>JUnit is a {@code provided} dependency of {@code common}: needed to compile these
 * annotations, but never passed on at runtime, because the check modules already bring JUnit.
 */
package com.labflow.common.junit;
