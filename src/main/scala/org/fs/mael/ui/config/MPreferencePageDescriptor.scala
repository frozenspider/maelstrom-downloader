package org.fs.mael.ui.config

/**
 * Preferences page descriptor.
 *
 * Used in {@code MPreferenceNode} to create a node in preference tree.
 * Also used in {@code BackendConfigUiImpl} (ignoring path) for entry-specific download properties.
 *
 * @author FS
 */
case class MPreferencePageDescriptor[Page <: MFieldEditorPreferencePage[_]](
  /** Name of this page, will be used as header, label AND as path element */
  name: String,
  /** Dot-separated path to the node in preference tree, {@code None} for root */
  pathOption: Option[String],
  /** Page class with zero-argument constructor available */
  clazz: Class[Page]
)
