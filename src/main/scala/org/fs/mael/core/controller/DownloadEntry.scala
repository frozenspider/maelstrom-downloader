package org.fs.mael.core.controller

import org.fs.mael.core.controller.view.DownloadDetailsView

/**
 * Entry for a specific download processor, implementation details may vary.
 * The only common thing is that it should provide unified details view.
 *
 * @author FS
 */
abstract class DownloadEntry(val details: DownloadDetailsView)
