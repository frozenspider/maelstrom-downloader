package org.fs.mael.ui.config

import org.eclipse.jface.preference.PreferenceNode
import org.eclipse.jface.resource.ImageDescriptor

class MPreferenceNode[Page <: MFieldEditorPreferencePage[_]](
  pageDescr: MPreferencePageDescriptor[Page],
  image:     ImageDescriptor
) extends PreferenceNode(
  (pageDescr.pathOption map (_ + ".") getOrElse "") + pageDescr.name,
  pageDescr.name,
  image,
  pageDescr.clazz.getName
)
