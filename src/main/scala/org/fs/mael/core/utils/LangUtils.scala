package org.fs.mael.core.utils

import org.apache.commons.lang3.reflect.FieldUtils

trait LangUtils {
  /** Get a nested field (with any access modifier) by path using reflection */
  @throws[NoSuchFieldException]
  def getNestedField(obj: AnyRef, path: List[String]): AnyRef = path match {
    case Nil =>
      obj
    case fieldName :: path =>
      val field = FieldUtils.getField(obj.getClass, fieldName, true)
      val innerObj = field.get(obj)
      getNestedField(innerObj, path)
  }
}

object LangUtils extends LangUtils  
