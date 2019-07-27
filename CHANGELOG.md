### SNAPSHOT
* Fixed `Content-Disposition: attachment; filename` not using ISO-8859-1 charset as per RFC 6266

### 1.0
* Added ability to automatically start new downloads
* Added ability to restart download from the beginning
* Added tray icon and minimize to tray setting
* Added download speed and remaining time columns, implemented related tracking and calculations
* Added support for proxy servers (SOCKS5)
* Added ability to set custom cookies
* Added ability to set custom headers
* Added "About" dialog
* Opening file folder on Windows OS now selects the file in explorer
* Double-clicking completed download now opens file folder instead of opening download properties
* Improved HTTPS support, added ability to disable SSL validation
* Remember main window size, position and maximized state 
* When creating new download, attempt is made to parse a plaintext HTTP/curl request from clipboard
* Fixed issue with stop not interrupting ongoing I/O.
  On faulty connection this might've lead to thread hanging until timeout was reached,
  or even indefinitely if timeout was 0
* (Internal) Implemented backend-specific settings framework
  * (Internal) Implemented global defaults serving as template for local per-download settings
  * (Internal) Implemented local settings that can refer to global ones without caching
    (so far - setting for connection proxy only)
* (Internal) Implemented migrations framework

### 0.2
* Added ability to specify download filename
* Added ability to specify a hash checksum to verify a downloaded file integrity
* Added ability to delete downloads with their local files
* Added ability to sort a download list by any column
* Added ability to edit download properties
  * Changing location or filename will cause the file to be moved, in a safe way and with a progress bar
* Reworked download location selection in Add Download dialog
* Fixed issue with HTTP downloader starving for data
* Fixed issue with line wrapping not working in download properties

### 0.1.2
* Fixed exit button in top menu not working 

### 0.1.1
* Added ability to copy download URI
* Added ability to open download folder
* Fixed issue with files downloaded via HTTP starting with 0-byte
* Fixed Linux shell script mistake
* Fixed issue on Linux system with main icon being too large
* Covered core and HTTP parts with tests

### 0.1
(Baseline version)
