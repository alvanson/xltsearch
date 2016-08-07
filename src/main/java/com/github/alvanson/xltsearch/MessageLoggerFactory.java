/* Copyright 2016 Evan A. Thompson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.alvanson.xltsearch;

import java.util.Map;
import java.util.HashMap;

import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

public class MessageLoggerFactory implements ILoggerFactory {
    public static final Map<String,Logger> loggerCache = new HashMap<>();

    public Logger getLogger(String name) {
        synchronized (loggerCache) {
            Logger logger = loggerCache.get(name);
            if (logger == null) {
                logger = new MessageLogger(name);
                loggerCache.put(name, logger);
            }
            return logger;
        }
    }
}
