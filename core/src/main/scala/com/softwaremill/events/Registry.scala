package com.softwaremill.events

import scala.reflect.ClassTag

case class Registry(
    eventListeners: Map[Class[_], List[EventListener[_]]],
    asyncEventListeners: Map[Class[_], List[EventListener[_]]],
    modelUpdates: Map[Class[_], List[ModelUpdate[_]]]
) {

  def registerEventListener[T: ClassTag](h: EventListener[T]): Registry = {
    val key = implicitly[ClassTag[T]].runtimeClass
    copy(eventListeners = eventListeners + (key -> (h :: eventListeners.getOrElse(key, Nil))))
  }

  def registerAsyncEventListener[T: ClassTag](h: EventListener[T]): Registry = {
    val key = implicitly[ClassTag[T]].runtimeClass
    copy(asyncEventListeners = asyncEventListeners + (key -> (h :: asyncEventListeners.getOrElse(key, Nil))))
  }

  def registerModelUpdate[T: ClassTag](h: ModelUpdate[T]): Registry = {
    val key = implicitly[ClassTag[T]].runtimeClass
    copy(modelUpdates = modelUpdates + (key -> (h :: modelUpdates.getOrElse(key, Nil))))
  }

  private[events] def lookupEventListeners[T](e: Event[T]): List[EventListener[T]] =
    // reverse so that listeners are returned in the order in which they were registered
    doLookup[T, EventListener](e.data.getClass, eventListeners)

  private[events] def hasAsyncEventListeners(e: Event[_]): Boolean =
    lookupAsyncEventListeners(e).nonEmpty

  private[events] def lookupAsyncEventListeners[T](e: Event[T]): List[EventListener[T]] =
    doLookup[T, EventListener](e.data.getClass, asyncEventListeners)

  private[events] def lookupModelUpdates[T](e: Event[T]): List[ModelUpdate[T]] =
    doLookup[T, ModelUpdate](e.data.getClass, modelUpdates)

  private def doLookup[T, W[_]](cls: Class[_], m: Map[Class[_], Any]): List[W[T]] = {
    m.get(cls).orElse {
      (Option(cls.getSuperclass).toList ++ cls.getInterfaces.toList)
        .view
        .map(doLookup(_, m))
        .find(_.nonEmpty)
    }.asInstanceOf[Option[List[W[T]]]].getOrElse(Nil).reverse
  }
}

object Registry {
  def apply(): Registry = Registry(Map(), Map(), Map())
}
