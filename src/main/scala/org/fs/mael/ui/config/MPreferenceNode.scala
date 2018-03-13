package org.fs.mael.ui.config

import org.eclipse.jface.preference.PreferenceNode
import org.eclipse.jface.resource.ImageDescriptor

class MPreferenceNode[P <: MFieldEditorPreferencePage](
  pageDescr: MPreferencePageDescriptor[P],
  image:     ImageDescriptor
) extends PreferenceNode(
  (pageDescr.pathOption map (_ + ".") getOrElse "") + pageDescr.name,
  pageDescr.name,
  image,
  pageDescr.clazz.getName
)
