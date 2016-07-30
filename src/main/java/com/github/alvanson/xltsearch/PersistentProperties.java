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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Properties;

public class PersistentProperties extends Properties {
    private File file;
    private String comments;
    private boolean persistent;  // able to persist changes to `file`

    public PersistentProperties(File file, String comments, InputStream defaults) {
        this.file = file;
        this.comments = comments;
        // load defaults
        if (defaults != null) {
            try (InputStream in = defaults) {
                load(defaults);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        // load properties
        this.persistent = true;
        if (file.exists()) {
            try (FileInputStream in = new FileInputStream(this.file)) {
                load(in);
            } catch (IOException ex) {
                this.persistent = false;
                ex.printStackTrace();
            }
        }
        // (attempt to) save properties
        if (this.persistent) {
            persist();
        }
    }

    public boolean isPersistent() {
        return persistent;
    }

    // attempts regardless of `persistent`
    private void persist() {
        try (FileOutputStream out = new FileOutputStream(file)) {
            store(out, comments);
            persistent = true;  // sucessfully stored
        } catch (IOException ex) {
            persistent = false;
            ex.printStackTrace();
        }
    }

    // persist on every change if `persistent`
    @Override
    public Object setProperty(String key, String value) {
        Object obj = super.setProperty(key, value);
        if (persistent) {
            persist();
        }
        return obj;
    }

    @Override
    public Object remove(Object key) {
        Object obj = super.remove(key);
        if (persistent) {
            persist();
        }
        return obj;
    }
}
