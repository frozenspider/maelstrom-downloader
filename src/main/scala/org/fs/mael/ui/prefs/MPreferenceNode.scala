package org.fs.mael.ui.prefs

import org.eclipse.jface.preference.PreferenceNode
import org.eclipse.jface.resource.ImageDescriptor

class MPreferenceNode[P <: MFieldEditorPreferencePage](
  id:    String,
  label: String,
  image: ImageDescriptor,
  clazz: Class[P]
) extends PreferenceNode(id, label, image, clazz.getName)
