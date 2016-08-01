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

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyListProperty;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;

abstract class BaseTask<V> extends Task<V> {
    private final ReadOnlyListWrapper<Message> messages =
        new ReadOnlyListWrapper<>(FXCollections.observableArrayList());

    protected final void addMessage(Message.Level level, String summary, String details) {
        final String className = getClass().getSimpleName();
        Platform.runLater(() ->
            messages.get().add(new Message(className, level, summary, details)));
    }

    protected final void addMessage(Message.Level level, String summary, Throwable ex) {
        final String className = getClass().getSimpleName();
        Platform.runLater(() ->
            messages.get().add(new Message(className, level, summary, ex)));
    }

    protected final ReadOnlyListProperty<Message> messagesProperty() {
        return messages.getReadOnlyProperty();
    }
}
