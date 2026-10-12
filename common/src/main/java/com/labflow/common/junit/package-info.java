/**
 * JUnit 5 support shared by both check modules: the labels that pick which checks run.
 *
 * <p>Each label is a composed annotation over JUnit's {@code @Tag}, so a typo is a compile
 * error rather than a silently empty run. The tag names themselves live in {@link
 * com.labflow.common.junit.Labels}.
 *
 * <p>It also holds {@link com.labflow.common.junit.CheckNameInterceptor}, which puts the running
 * check's name into the log context so every log line can say which check it belongs to,
 * {@link com.labflow.common.junit.CheckCountListener}, which logs how many lab checks a run
 * executed, and {@link com.labflow.common.junit.LabChecksAllureListener}, which lets only the
 * lab checks into the Allure report. Both listeners decide "is this a lab check" with the same
 * rule, {@link com.labflow.common.junit.Labels#isLabCheck}.
 *
 * <p>JUnit and Allure's JUnit Platform listener are {@code provided} dependencies of
 * {@code common}: needed to compile these classes, but never passed on at runtime, because the
 * check modules already bring them.
 */
package com.labflow.common.junit;
