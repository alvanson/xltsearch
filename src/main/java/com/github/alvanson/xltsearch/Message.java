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

import java.io.PrintWriter;
import java.io.StringWriter;

class Message {
    static enum Level {
        INFO, WARN, ERROR
    }

    final String from;
    final Level level;
    final String summary;
    final String details;

    Message(String from, Level level, String summary, String details) {
        this.from = from;
        this.level = level;
        this.summary = summary;
        this.details = details;
    }

    Message(String from, Level level, String summary, Throwable ex) {
        this(from, level, summary, getStackTrace(ex));
    }

    static String getStackTrace(Throwable ex) {
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
