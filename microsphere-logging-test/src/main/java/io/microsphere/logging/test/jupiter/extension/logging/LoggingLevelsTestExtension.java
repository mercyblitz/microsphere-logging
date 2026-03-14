/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.microsphere.logging.test.jupiter.extension.logging;

import io.microsphere.logging.Logging;
import io.microsphere.logging.test.jupiter.LoggingLevelsTest;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ClassTemplateInvocationContext;
import org.junit.jupiter.api.extension.ClassTemplateInvocationContextProvider;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;
import org.junit.jupiter.api.extension.TestTemplateInvocationContextProvider;

import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;

import static io.microsphere.collection.ListUtils.newArrayList;
import static io.microsphere.collection.Lists.ofList;
import static io.microsphere.collection.MapUtils.newFixedHashMap;
import static io.microsphere.logging.LoggingUtils.loadAll;
import static io.microsphere.util.ClassLoaderUtils.getClassLoader;

/**
 * The {@link Extension} repeats the execution with the specified logging levels.
 *
 * @author <a href="mailto:mercyblitz@gmail.com">Mercy</a>
 * @see TestTemplateInvocationContextProvider
 * @see TestTemplateInvocationContext
 * @since 1.0.0
 */
public class LoggingLevelsTestExtension implements ClassTemplateInvocationContextProvider,
        TestTemplateInvocationContextProvider {

    @Override
    public boolean supportsClassTemplate(ExtensionContext context) {
        return context.getRequiredTestClass().isAnnotationPresent(LoggingLevelsTest.class);
    }

    @Override
    public boolean supportsTestTemplate(ExtensionContext context) {
        return context.getRequiredTestMethod().isAnnotationPresent(LoggingLevelsTest.class);
    }

    @Override
    public Stream<TestTemplateInvocationContext> provideTestTemplateInvocationContexts(ExtensionContext context) {
        return (Stream) provideInvocationContexts(context);
    }

    private LoggingLevelsTest getLoggingLevelsTest(ExtensionContext context) {
        LoggingLevelsTest loggingLevelsTest = context.getRequiredTestClass().getAnnotation(LoggingLevelsTest.class);
        if (loggingLevelsTest == null) {
            return context.getRequiredTestMethod().getAnnotation(LoggingLevelsTest.class);
        }
        return loggingLevelsTest;
    }

    @Override
    public Stream<? extends ClassTemplateInvocationContext> provideClassTemplateInvocationContexts(ExtensionContext context) {
        return provideInvocationContexts(context);
    }

    protected Stream<LoggingLevelTestTemplateInvocationContext> provideInvocationContexts(ExtensionContext context) {
        LoggingLevelsTest loggingLevelsTest = getLoggingLevelsTest(context);
        String[] levels = loggingLevelsTest.levels();
        Class<?> testClass = context.getRequiredTestClass();
        String loggerName = testClass.getPackage().getName();
        ClassLoader classLoader = getClassLoader(testClass);
        List<Logging> loggins = loadAll(classLoader);

        Map<Logging, String> loggingsWithOriginalLevels = newFixedHashMap(loggins.size());
        for (Logging logging : loggins) {
            String originalLevel = logging.getLoggerLevel(loggerName);
            loggingsWithOriginalLevels.put(logging, originalLevel);
        }

        int length = levels.length;

        List<LoggingLevelTestTemplateInvocationContext> contexts = newArrayList(length);

        for (int i = 0; i < length; i++) {
            String level = levels[i];
            contexts.add(i, new LoggingLevelTestTemplateInvocationContext(loggerName, level, i, loggingsWithOriginalLevels));
        }

        return contexts.stream();
    }

    static class LoggingLevelTestTemplateInvocationContext implements ClassTemplateInvocationContext, TestTemplateInvocationContext {

        private final String loggerName;

        private final String level;

        private final int index;

        private final Map<Logging, String> loggingsWithOriginalLevels;

        LoggingLevelTestTemplateInvocationContext(String loggerName, String level, int index, Map<Logging, String> loggingsWithOriginalLevels) {
            this.loggerName = loggerName;
            this.level = level;
            this.index = index;
            this.loggingsWithOriginalLevels = loggingsWithOriginalLevels;
        }

        @Override
        public String getDisplayName(int invocationIndex) {
            return "[" + this.level + "]";
        }

        @Override
        public List<Extension> getAdditionalExtensions() {
            loggingsWithOriginalLevels.keySet().forEach(logging -> {
                logging.setLoggerLevel(this.loggerName, this.level);
            });

            return ofList(
                    (AfterEachCallback) context -> {
                        // Reset the original level
                        for (Entry<Logging, String> entry : loggingsWithOriginalLevels.entrySet()) {
                            Logging logging = entry.getKey();
                            String originalLevel = entry.getValue();
                            logging.setLoggerLevel(this.loggerName, originalLevel);
                        }
                    }, new ParameterResolver() {
                        @Override
                        public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
                            Parameter parameter = parameterContext.getParameter();
                            Class<?> parameterType = parameter.getType();
                            return String.class.equals(parameterType) || int.class.equals(parameterType);
                        }

                        @Override
                        public @Nullable Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
                            Parameter parameter = parameterContext.getParameter();
                            Class<?> parameterType = parameter.getType();
                            if (String.class.equals(parameterType)) {
                                return level;
                            } else if (int.class.equals(parameterType)) {
                                return index;
                            }
                            return null;
                        }
                    }
            );
        }

        public void prepareInvocation(ExtensionContext context) {
        }
    }
}
