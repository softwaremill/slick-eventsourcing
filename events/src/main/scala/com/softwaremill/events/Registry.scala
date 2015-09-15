package com.softwaremill.events

import scala.reflect.ClassTag

case class Registry(eventListeners: Map[Class[_], List[EventListener[_]]], modelUpdates: Map[Class[_], List[ModelUpdate[_]]]) {
  def registerEventListener[T: ClassTag](h: EventListener[T]): Registry = {
    val key = implicitly[ClassTag[T]].runtimeClass
    copy(eventListeners = eventListeners + (key -> (h :: eventListeners.getOrElse(key, Nil))))
  }

  def registerModelUpdate[T: ClassTag](h: ModelUpdate[T]): Registry = {
    val key = implicitly[ClassTag[T]].runtimeClass
    copy(modelUpdates = modelUpdates + (key -> (h :: modelUpdates.getOrElse(key, Nil))))
  }

  private[events] def lookupEventListener[T](e: Event[T]): List[EventListener[T]] =
    // reverse so that listeners are returned in the order in which they were registered
    doLookup(e.data.getClass, eventListeners).asInstanceOf[Option[List[EventListener[T]]]].getOrElse(Nil).reverse

  private[events] def lookupModelUpdate[T](e: Event[T]): List[ModelUpdate[T]] =
    doLookup(e.data.getClass, modelUpdates).asInstanceOf[Option[List[ModelUpdate[T]]]].getOrElse(Nil).reverse

  private def doLookup(cls: Class[_], m: Map[Class[_], Any]): Option[Any] = {
    m.get(cls).orElse {
      (Option(cls.getSuperclass).toList ++ cls.getInterfaces.toList)
        .view
        .map(doLookup(_, m))
        .collectFirst { case Some(t) => t }
    }
  }
}

object Registry {
  def apply(): Registry = Registry(Map(), Map())
}
