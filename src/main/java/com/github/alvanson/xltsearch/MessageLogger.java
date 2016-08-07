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

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MarkerIgnoringBase;
import org.slf4j.helpers.MessageFormatter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedList;
import javafx.application.Platform;
import javafx.beans.property.ListProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;

class MessageLogger extends MarkerIgnoringBase {
    private static final SimpleObjectProperty<Message.Level> logLevel =
        new SimpleObjectProperty<>(Message.Level.WARN);
    private static final SimpleListProperty<Message> messages =
        new SimpleListProperty<>(FXCollections.observableList(new LinkedList<>()));

    MessageLogger(String name) {
        this.name = name.substring(name.lastIndexOf(".") + 1);
    }

    private void add(Message.Level level, String msg) {
        final long now = System.currentTimeMillis();
        Platform.runLater(() ->
            messages.get().add(new Message(now, level, name, msg, "")));
    }
    private void add(Message.Level level, String msg, Throwable t) {
        final long now = System.currentTimeMillis();
        Platform.runLater(() ->
            messages.get().add(new Message(now, level, name, msg, getStackTrace(t))));
    }
    private void add(Message.Level level, FormattingTuple tp) {
        if (tp.getThrowable() == null) {
            add(level, tp.getMessage());
        } else {
            add(level, tp.getMessage(), tp.getThrowable());
        }
    }

    private void log(Message.Level level, String msg) {
        if (level.compareTo(logLevel.get()) >= 0) {
            add(level, msg);
        }
    }
    private void log(Message.Level level, String format, Object... arguments) {
        if (level.compareTo(logLevel.get()) >= 0) {
            add(level, MessageFormatter.arrayFormat(format, arguments));
        }
    }
    private void log(Message.Level level, String format, Object arg1, Object arg2) {
        if (level.compareTo(logLevel.get()) >= 0) {
            add(level, MessageFormatter.format(format, arg1, arg2));
        }
    }
    private void log(Message.Level level, String format, Object arg) {
        if (level.compareTo(logLevel.get()) >= 0) {
            add(level, MessageFormatter.format(format, arg));
        }
    }
    private void log(Message.Level level, String msg, Throwable t) {
        if (level.compareTo(logLevel.get()) >= 0) {
            add(level, msg, t);
        }
    }

    public void trace(String msg) {
        log(Message.Level.TRACE, msg);
    }
    public void trace(String format, Object... arguments) {
        log(Message.Level.TRACE, format, arguments);
    }
    public void trace(String format, Object arg1, Object arg2) {
        log(Message.Level.TRACE, format, arg1, arg2);
    }
    public void trace(String format, Object arg) {
        log(Message.Level.TRACE, format, arg);
    }
    public void trace(String msg, Throwable t) {
        log(Message.Level.TRACE, msg, t);
    }
    public boolean isTraceEnabled() {
        return (Message.Level.TRACE.compareTo(logLevel.get()) >= 0);
    }

    public void debug(String msg) {
        log(Message.Level.DEBUG, msg);
    }
    public void debug(String format, Object... arguments) {
        log(Message.Level.DEBUG, format, arguments);
    }
    public void debug(String format, Object arg1, Object arg2) {
        log(Message.Level.DEBUG, format, arg1, arg2);
    }
    public void debug(String format, Object arg) {
        log(Message.Level.DEBUG, format, arg);
    }
    public void debug(String msg, Throwable t) {
        log(Message.Level.DEBUG, msg, t);
    }
    public boolean isDebugEnabled() {
        return (Message.Level.DEBUG.compareTo(logLevel.get()) >= 0);
    }

    public void info(String msg) {
        log(Message.Level.INFO, msg);
    }
    public void info(String format, Object... arguments) {
        log(Message.Level.INFO, format, arguments);
    }
    public void info(String format, Object arg1, Object arg2) {
        log(Message.Level.INFO, format, arg1, arg2);
    }
    public void info(String format, Object arg) {
        log(Message.Level.INFO, format, arg);
    }
    public void info(String msg, Throwable t) {
        log(Message.Level.INFO, msg, t);
    }
    public boolean isInfoEnabled() {
        return (Message.Level.INFO.compareTo(logLevel.get()) >= 0);
    }

    public void warn(String msg) {
        log(Message.Level.WARN, msg);
    }
    public void warn(String format, Object... arguments) {
        log(Message.Level.WARN, format, arguments);
    }
    public void warn(String format, Object arg1, Object arg2) {
        log(Message.Level.WARN, format, arg1, arg2);
    }
    public void warn(String format, Object arg) {
        log(Message.Level.WARN, format, arg);
    }
    public void warn(String msg, Throwable t) {
        log(Message.Level.WARN, msg, t);
    }
    public boolean isWarnEnabled() {
        return (Message.Level.WARN.compareTo(logLevel.get()) >= 0);
    }

    public void error(String msg) {
        log(Message.Level.ERROR, msg);
    }
    public void error(String format, Object... arguments) {
        log(Message.Level.ERROR, format, arguments);
    }
    public void error(String format, Object arg1, Object arg2) {
        log(Message.Level.ERROR, format, arg1, arg2);
    }
    public void error(String format, Object arg) {
        log(Message.Level.ERROR, format, arg);
    }
    public void error(String msg, Throwable t) {
        log(Message.Level.ERROR, msg, t);
    }
    public boolean isErrorEnabled() {
        return (Message.Level.ERROR.compareTo(logLevel.get()) >= 0);
    }

    static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    static ObjectProperty<Message.Level> logLevelProperty() { return logLevel; }
    static ListProperty<Message> messagesProperty() { return messages; }
}
