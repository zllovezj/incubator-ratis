/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ratis;

import org.apache.log4j.Level;
import org.apache.ratis.conf.ConfUtils;
import org.apache.ratis.util.*;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.rules.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public abstract class BaseTest {
  public final Logger LOG = LoggerFactory.getLogger(getClass());

  {
    LogUtils.setLogLevel(ConfUtils.LOG, Level.WARN);
    LogUtils.setLogLevel(FileUtils.LOG, Level.TRACE);
  }

  @Rule
  public final Timeout globalTimeout = new Timeout(getGlobalTimeoutSeconds() * 1000);

  @Rule
  public final TestName testName = new TestName();

  public int getGlobalTimeoutSeconds() {
    return 100;
  }

  private static final Supplier<File> rootTestDir = JavaUtils.memoize(
      () -> JavaUtils.callAsUnchecked(() -> {
        final File dir = new File(System.getProperty("test.build.data", "target/test/data"),
            Integer.toHexString(ThreadLocalRandom.current().nextInt()));
        if (dir.exists() && !dir.isDirectory()) {
          throw new IOException(dir + " already exists and is not a directory");
        } else if (!dir.exists() && !dir.mkdirs()) {
          throw new IOException("Cannot create test directory " + dir);
        }
        return dir;
      }));


  public static File getRootTestDir() {
    return rootTestDir.get();
  }

  public File getClassTestDir() {
    return new File(getRootTestDir(), getClass().getSimpleName());
  }

  public File getTestDir() {
    return new File(getClassTestDir(), testName.getMethodName());
  }

  @SafeVarargs
  public static void assertThrowable(
      String description, Throwable t,
      Class<? extends Throwable> exceptedThrowableClass, Logger log,
      Class<? extends Throwable>... exceptedCauseClasses) {
    if (log != null) {
      log.info("The test \"" + description + "\" throws " + t.getClass().getSimpleName(), t);
    }
    Assert.assertEquals(exceptedThrowableClass, t.getClass());

    for (Class<? extends Throwable> expectedCause : exceptedCauseClasses) {
      final Throwable previous = t;
      t = Objects.requireNonNull(previous.getCause(),
          () -> "previous.getCause() == null for previous=" + previous);
      Assert.assertEquals(expectedCause, t.getClass());
    }
  }

  @SafeVarargs
  public static void testFailureCase(
      String description, CheckedRunnable<?> testCode,
      Class<? extends Throwable> exceptedThrowableClass, Logger log,
      Class<? extends Throwable>... exceptedCauseClasses) {
    boolean caught = false;
    try {
      testCode.run();
    } catch (Throwable t) {
      caught = true;
      assertThrowable(description, t, exceptedThrowableClass, log, exceptedCauseClasses);
    }
    if (!caught) {
      Assert.fail("The test \"" + description + "\" does not throw anything.");
    }
  }

  @SafeVarargs
  public final void testFailureCase(
      String description, CheckedRunnable<?> testCode,
      Class<? extends Throwable> exceptedThrowableClass,
      Class<? extends Throwable>... exceptedCauseClasses) {
    testFailureCase(description, testCode, exceptedThrowableClass, LOG, exceptedCauseClasses);
  }

  @SafeVarargs
  public static void testFailureCaseAsync(
      String description, Supplier<CompletableFuture<?>> testCode,
      Class<? extends Throwable> exceptedThrowableClass, Logger log,
      Class<? extends Throwable>... exceptedCauseClasses) {
    try {
      testCode.get().join();
      Assert.fail("The test \"" + description + "\" does not throw anything.");
    } catch (Throwable t) {
      t = JavaUtils.unwrapCompletionException(t);
      assertThrowable(description, t, exceptedThrowableClass, log, exceptedCauseClasses);
    }
  }

  @SafeVarargs
  public final void testFailureCaseAsync(
      String description, Supplier<CompletableFuture<?>> testCode, Class<? extends Throwable> exceptedThrowableClass,
      Class<? extends Throwable>... exceptedCauseClasses) {
    testFailureCaseAsync(description, testCode, exceptedThrowableClass, LOG, exceptedCauseClasses);
  }
}
