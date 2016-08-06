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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import javafx.concurrent.Task;

// desktop.open() must be executed in a separate task
class OpenFileTask extends Task<Void> {
    private final Desktop desktop = Desktop.getDesktop();
    private final File file;

    private final Logger logger = LoggerFactory.getLogger(OpenFileTask.class);

    OpenFileTask(File file) {
        this.file = file;
    }

    @Override
    protected Void call() {
        try {
            desktop.open(file);
        } catch (IOException ex) {
            logger.error("Could not open file {}", file.getName(), ex);
        }
        return null;
    }
}
