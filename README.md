XLTSearch
=========

XLTSearch is a high-performance, portable and configurable desktop search application / information retrieval system intended for fast, full-text and metadata searches over a large but relatively unchanging collection of documents.  XLTSearch uses the JavaFX platform (X), the Apache Lucene full-text search library (L) and the Apache Tika content analysis toolkit (T).  Other than Java and JavaFX 8, XLTSearch has no other external dependencies, enabling the creation of portable, fully searchable document repositories.

**Current version:** XLTSearch 0.0.1 includes Apache Lucene 4.6.1 and Apache Tika 1.13.

Motivation
----------

XLTSearch is inspired by two similar open-source projects: [DocFetcher](http://docfetcher.sourceforge.net) and [FXDesktopSearch](https://github.com/mirkosertic/FXDesktopSearch). Differences of note from these projects are:

  * XLTSearch supports all file types supported by Apache Tika.
  * XLTSearch extracts a common set of metadata from all documents (where available) and makes these fields searchable.
  * XLTSearch supports multiple indices over a set of documents, enabling the use of different analyzers / configurations for different applications.
  * XLTSearch exposes the full functionality of the "classic" Lucene query parser to the user.
  * XLTSearch is permissively licensed under the Apache License 2.0.

Unlike the above projects, however, XLTSearch does **not** monitor folders for updates.  XLTSearch requires the user to explicitly initiate index updates.  To enable portability (e.g. searchable document repository on a removable drive), XLTSearch assumes that all files in a selected folder are to be indexed and searched and there is currently no functionality to exclude specific files or folders: the selected folder and its entire contents are recursively indexed.

A folder may have any number of configurations, but only one folder and one configuration can be loaded at a given time.  XLTSearch will index and search the files according to the selected configuration.

Installation and Usage
----------------------

XLTSearch is packaged with its dependencies into a single executable "uber-jar" with no installation required.  The jar file can be run either by double-clicking or by running `java -jar xltsearch.jar` at a command line.

### System Requirements

  * Java JRE 8u40 or later
  * JavaFX 8u40 or later

A 64-bit JRE is recommended.  If a 32-bit JRE is used, passing the option `-Xmx1g` may solve non-responsiveness or out-of-memory issues during indexing.

JavaFX is normally included in the Java JRE, but on some platforms, JavaFX is distributed separately.  If you are using a Debian or Ubuntu-based system, JavaFX is included in the `openjfx` package.  Run `sudo apt-get install openjfx` to install JavaFX.

Note that XLTSearch has **not** yet been tested on Java 9.

Build Instructions
------------------

To maintain a consistent development and build environment, XLTSearch is built using Apache Maven inside a Vagrant virtual machine. The `Vagrantfile` for the build environment is provided in this repository. The "uber-jar" is created using `mvn package` in the VM.

Contributing
------------

All contributions (e.g. issue reports, pull requests, questions and comments) are encouraged.  Suggestions on how to add new IR technologies, improve the code or prune / make better use of the dependency tree are especially welcome.

Authors
-------

  * **Evan Thompson** (alvanson) - principal author

License
-------

XLTSearch is licensed under the Apache License 2.0 and is provided on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  Please ensure all data is backed up prior to running XLTSearch.

Acknowledgements
-----------------

XLTSearch includes Apache Lucene and Apache Tika.  Refer to `NOTICE.txt` for attribution notices.
